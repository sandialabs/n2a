/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImportNeuroML implements ImportModel
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void process (Path source, String name)
    {
        if (PluginNeuroML.partMap == null) PluginNeuroML.partMap = new PartMap ();

        ImportJob job = new ImportJob (PluginNeuroML.partMap);
        job.process (source);
        job.postprocess ();

        MNode mainModel = job.models.child (job.modelName);
        job.models.clear (job.modelName);

        UndoManager um = MainFrame.undoManager;
        um.addEdit (new CompoundEdit ());
        while (job.models.size () > 0) addModel (job.models.iterator ().next (), job.models, um);
        // Save the best for last. That is, ensure that the main model is the one selected in the UI
        // after all add operations are completed.
        if (mainModel != null)
        {
            if (name == null) name = job.modelName;
            um.apply (new AddDoc (name, mainModel));
        }
        um.endCompoundEdit ();
    }

    public void addModel (MNode m, MNode models, UndoManager um)
    {
        String key = m.key ();
        models.clear (key);

        // Scan for any models we may depend on, and add them first.
        // This minimizes redundant equations. For example, the NeuroML core
        // model files frequently repeat equations that should be inherited.
        for (MNode c : m)
        {
            String inherit = "";
            if (c.key ().equals ("$inherit"))
            {
                inherit = c.get ().replace ("\"", "");
            }
            else if (c.child ("$inherit") != null)
            {
                inherit = c.get ("$inherit").replace ("\"", "");
            }
            MNode d = models.child (inherit);
            if (d != null) addModel (d, models, um);
        }

        AddDoc add = new AddDoc (key, m);
        add.setSilent ();
        um.apply (add);
    }

    @Override
    public float matches (Path source)
    {
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".nml")) return 1;

        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            String line = "";
            while (line.isEmpty ())
            {
                line = reader.readLine ();
                if (line == null) return 0;
                line = line.trim ();
            }
            if (line.startsWith ("<Lems")) return 1.0f;
            if (line.startsWith ("<?xml")) return 0.8f;
            // To be absolutely certain, could check for top-level tags that normally start a NeuroML section.
        }
        catch (IOException e) {}
        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            if (suffix.equalsIgnoreCase (".nml")) return true;
            if (suffix.equalsIgnoreCase (".xml")) return true;
        }
        return false;
    }
}
