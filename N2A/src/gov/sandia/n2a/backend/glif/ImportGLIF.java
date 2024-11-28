package gov.sandia.n2a.backend.glif;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

public class ImportGLIF implements ImportModel
{
    @Override
    public String getName ()
    {
        return "GLIF";
    }

    @Override
    public void process (Path source, String name) throws Exception
    {
        String filename = source.getFileName ().toString ();
        int lastDot = filename.lastIndexOf ('.');
        String suffix = "";
        if (lastDot >= 0)
        {
            suffix = filename.substring (lastDot + 1).toLowerCase ();
            filename = filename.substring (0, lastDot);
        }

        MVolatile model = new MVolatile ();
        if (suffix.equals ("json"))
        {
            try (BufferedReader reader = Files.newBufferedReader (source))
            {
                parseConfig (reader, model);
            }
        }
        else if (suffix.equals ("zip"))
        {
            try (ZipFile archive = new ZipFile (source.toFile ()))
            {
                ZipEntry e = archive.getEntry ("neuron_config.json");
                if (e != null) parseConfig (new InputStreamReader (archive.getInputStream (e)), model);

                e = archive.getEntry ("model_metadata.json");
                if (e != null) parseMetadata (new InputStreamReader (archive.getInputStream (e)), model);
            }
        }
        if (model.isEmpty ()) return;  // TODO: throw an informative exception.

        // Save the model.
        if (name == null  ||  name.isBlank ()) name = model.get ("$meta", "specimen").split (";")[0];
        if (                  name.isBlank ()) name = filename;
        MainFrame.undoManager.apply (new AddDoc (name, model));
    }

    @Override
    public float matches (Path source)
    {
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        String suffix = "";
        if (lastDot >= 0) suffix = name.substring (lastDot + 1).toLowerCase ();

        // Try to open a ZIP file and see if it contains the right files.
        if (suffix.equals ("zip"))
        {
            try (ZipFile archive = new ZipFile (source.toFile ()))
            {
                if (archive.getEntry ("neuron_config.json") != null) return 1.0f;
            }
            catch (IOException e)
            {
                return 0;
            }
        }

        // Attempt any JSON, though we are less certain it is a match.
        // For more certainty, we could read in the JSON and check for indicative fields.
        if (suffix.equals ("json")) return 0.5f;

        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        String suffix = "";
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0) suffix = name.substring (lastDot + 1).toLowerCase ();
        if (suffix.equals ("zip" )) return true;
        if (suffix.equals ("json")) return true;
        return false;
    }

    public void parseConfig (Reader reader, MNode model)
    {
        MVolatile m = new MVolatile ();
        JSON json = new JSON ();
        try
        {
            json.read (m, reader);

            model.set ("Cell GLIF", "$inherit");
            double dt = m.getOrDefault (0.0, "dt");
            if (dt == 0) dt = 1e-5;
            else         model.set (Scalar.print (dt), "$meta", "dt");  // As a hint for testing. Otherwise, we don't want to embed $t' into an individual neuron model.

            double Voffset    = m  .getOrDefault (0.0, "El_reference");
            double E          = m  .getOrDefault (0.0, "El")             + Voffset;
            double Vinit      = m  .getOrDefault (0.0, "init_voltage")   + Voffset;
            double theta_init = m  .getOrDefault (0.0, "init_threshold") + Voffset;
            MNode  vrm        = m  .childOrEmpty ("voltage_reset_method", "params");
            double f_v        = vrm.getOrDefault (0.0, "a");
            double delta_v    = vrm.getOrDefault (0.0, "b");
            MNode  tdm        = m  .childOrEmpty ("threshold_dynamics_method", "params");
            double a_s        = tdm.getOrDefault (0.0, "a_spike");
            double b_s        = tdm.getOrDefault (0.0, "b_spike");
            MNode  coeffs     = m  .childOrEmpty ("coeffs");
            double a_v        = tdm.getOrDefault (0.0, "a_voltage") * coeffs.getOrDefault (1.0, "a"); // If zero, we won't override.
            double b_v        = tdm.getOrDefault (0.0, "b_voltage") * coeffs.getOrDefault (1.0, "b");
            double C          = m  .getOrDefault (0.0, "C")         * coeffs.getOrDefault (1.0, "C");
            double R          = m  .getOrDefault (0.0, "R_input")   / coeffs.getOrDefault (1.0, "G");
            double theta_inf  = m  .getOrDefault (0.0, "th_inf")    * coeffs.getOrDefault (1.0, "th_inf") + Voffset;
            double rp         = m  .getOrDefault (0.0, "spike_cut_length") * dt;

            if (C != 0)          model.set (Scalar.print (C)          + "F",  "C");
            if (R != 0)          model.set (Scalar.print (1 / R)      + "S",  "G");
            if (E != 0.07)       model.set (Scalar.print (E)          + "V",  "E");
            if (Vinit != E)      model.set (Scalar.print (Vinit)      + "V",  "Vinit");
            if (theta_init != 0) model.set (Scalar.print (theta_init) + "V",  "Θinit");
            if (theta_inf != 0)  model.set (Scalar.print (theta_inf)  + "V",  "Θ∞");
            if (delta_v != 0)    model.set (Scalar.print (delta_v)    + "V",  "δV");
            if (a_s != 0)        model.set (Scalar.print (a_s)        + "V",  "δΘs");
            if (b_s != 0)        model.set (Scalar.print (b_s)        + "Hz", "b_s");
            if (a_v != 0)        model.set (Scalar.print (a_v)        + "Hz", "a_v");
            if (b_v != 0)        model.set (Scalar.print (b_v)        + "Hz", "b_v");
            if (f_v != 0)        model.set (Scalar.print (f_v),               "f_v");  // unitless ratio
            if (rp != 0)         model.set (Scalar.print (rp)         + "s",  "refractoryPeriod");

            // Create after-spike currents
            MNode tau     = m.childOrEmpty ("asc_tau_array");
            MNode dI      = m.childOrEmpty ("asc_amp_array");
            MNode dIscale = coeffs.childOrEmpty ("asc_amp_array");
            MNode init    = m.childOrEmpty ("init_AScurrents");
            MNode r       = m.childOrEmpty ("AScurrent_reset_method", "params", "r");
            for (int i = 0; i < tau.size (); i++)
            {
                MNode I = model.childOrCreate ("After Spike Current " + (i + 1));
                I.set ("GLIF After Spike Current", "$inherit");

                I.set (Scalar.print (1.0 / tau.getDouble    (i))                                 + "Hz", "k");
                I.set (              init.getOrDefault ("0", i)                                  + "A",  "init");
                I.set (Scalar.print (dI  .getOrDefault (0.0, i) * dIscale.getOrDefault (1.0, i)) + "A",  "δI");
                I.set (Scalar.print (r   .getOrDefault (1.0, i)),                                        "r");  // unitless ratio
            }
        }
        catch (IOException e) {}
    }

    public void apply (MNode from, MNode model, String fromKey, String modelKey)
    {
        MNode n = from.child (fromKey);
        if (n == null) return;
        model.set (n.getDouble (), modelKey);
    }

    public void parseMetadata (Reader reader, MNode model)
    {
        MVolatile m = new MVolatile ();
        JSON json = new JSON ();
        try
        {
            json.read (m, reader);

            MNode name = m.child ("name");
            if (name != null) model.set (name.get (), "$meta", "model");

            MNode specimen = m.child ("specimen");
            if (specimen == null) return;

            MNode ephys = specimen.child ("ephys_result_id");
            if (ephys != null) model.set (Scalar.print (ephys.getDouble ()), "$meta", "ephys_result_id");  // To help retrieve data for validation of parameters.

            name = specimen.child ("name");
            if (name != null) model.set (name.get (), "$meta", "specimen");
        }
        catch (IOException e) {}
    }
}
