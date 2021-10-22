/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.backend.c.JobC;
import gov.sandia.n2a.backend.python.JobPython;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.OutputParser;
import gov.sandia.n2a.ui.jobs.OutputParser.Column;
import gov.sandia.n2a.ui.jobs.Table;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;
import gov.sandia.n2a.ui.studies.PanelStudy.SampleTableModel;
import gov.sandia.n2a.ui.studies.Study;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import javax.swing.JFrame;
import javax.swing.JOptionPane;


public class Main
{
    public static void main (String[] args)
    {
        // Parse command line
        ArrayList<String> pluginClassNames = new ArrayList<String> ();
        ArrayList<Path>   pluginDirs       = new ArrayList<Path> ();
        MNode record = new MVolatile ();
        String headless = "";
        for (String arg : args)
        {
            if      (arg.startsWith ("-plugin="    )) pluginClassNames.add            (arg.substring (8));
            else if (arg.startsWith ("-pluginDir=" )) pluginDirs      .add (Paths.get (arg.substring (11)).toAbsolutePath ());
            else if (arg.startsWith ("-param="     )) processParamFile (arg.substring (7), record);
            else if (arg.startsWith ("-install"    )) headless = "install";
            else if (arg.startsWith ("-csv"        )) record.set (true, "$metadata", "csv");
            else if (arg.startsWith ("-run="))
            {
                record.set (arg.substring (5), "$inherit");
                headless = "run";
            }
            else if (arg.startsWith ("-study="))
            {
                record.set (arg.substring (7), "$inherit");
                headless = "study";
            }
            else if (! arg.startsWith ("-"))
            {
                String[] pieces = arg.split ("=", 2);
                String keys = pieces[0];
                String value = "";
                if (pieces.length == 2) value = pieces[1];
                record.set (value, keys.split ("\\."));
            }
        }

        if (headless.isEmpty ()) setUncaughtExceptionHandler (null);

        // Set global application properties.
        AppData.properties.set ("Neurons to Algorithms", "name");
        AppData.properties.set ("N2A",                   "abbreviation");
        AppData.properties.set ("1.1",                   "version");
        AppData.properties.set (! headless.isEmpty (),   "headless");
        AppData.checkInitialDB ();
        Path resourceDir = Paths.get (AppData.properties.get ("resourceDir"));

        // Load plugins
        pluginClassNames.add ("gov.sandia.n2a.backend.internal.InternalPlugin");
        pluginClassNames.add ("gov.sandia.n2a.backend.xyce.XycePlugin");
        pluginClassNames.add ("gov.sandia.n2a.backend.c.PluginC");
        pluginClassNames.add ("gov.sandia.n2a.backend.neuroml.PluginNeuroML");
        pluginClassNames.add ("gov.sandia.n2a.backend.neuron.PluginNeuron");
        Path pluginDir = resourceDir.resolve ("plugins").toAbsolutePath ();
        try {Files.createDirectories (pluginDir);}
        catch (IOException e) {}
        pluginDirs.add (pluginDir);
        PluginManager.initialize (new N2APlugin (), pluginClassNames, pluginDirs);

        if (! headless.isEmpty ())
        {
            if      (headless.equals ("run"    )) runHeadless   (record);
            else if (headless.equals ("study"  )) studyHeadless (record);
            else if (headless.equals ("install"))
            {
                try
                {
                    JobC jobC = new JobC (null);
                    jobC.runtimeDir = resourceDir.resolve ("backend").resolve ("c");
                    jobC.unpackRuntime ();

                    JobPython jobPython = new JobPython ();
                    jobPython.runtimeDir = resourceDir.resolve ("backend").resolve ("python");
                    jobPython.unpackRuntime ();
                }
                catch (Exception e)
                {
                    System.err.println ("Failed to unpack runtime resources.");
                }

                AppData.quit ();  // Flush new DB data.
            }
            return;
        }

        // Create the main frame.
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                SettingsLookAndFeel.instance.load ();  // Note that SettingsLookAndFeel is instantiated when the plugin system is initialized above.
                new MainFrame ();
                setUncaughtExceptionHandler (MainFrame.instance);
            }
        });
    }

    public static void processParamFile (String fileName, MNode record)
    {
        // Detect type
        // We only accept an N2A file or a Dakota parameter file.
        Path file = Paths.get (fileName);
        try (BufferedReader reader = Files.newBufferedReader (file))
        {
            reader.mark (128);
            String line = reader.readLine ();
            if (line == null) throw new IOException ();
            if (line.startsWith ("N2A.schema"))  // N2A file
            {
                reader.reset ();
                Schema.readAll (record, reader);
            }
            else if (line.contains ("variables"))  // Dakota
            {
                record.set ("", "$metadata", "dakota");  // Existence of key indicates that a Dakota-style response is requested.

                // Variables
                int count = Integer.parseUnsignedInt (line.trim ().split ("\\s+")[0]);
                for (int i = 0; i < count; i++)
                {
                    line = reader.readLine ();
                    if (line == null) throw new IOException ();
                    String[] pieces = line.trim ().split ("\\s+");
                    record.set (pieces[0], pieces[1].split ("\\."));
                }

                // Outputs (active-set vector)
                line = reader.readLine ();
                if (line == null) return;
                count = Integer.parseUnsignedInt (line.trim ().split ("\\s+")[0]);
                for (int i = 0; i < count; i++)
                {
                    line = reader.readLine ();
                    if (line == null) throw new IOException ();
                    String[] pieces = line.trim ().split ("\\s+");
                    // Ignore value. N2A only returns function result, not gradient or Hessian. The user is responsible to configure Dakota accordingly.
                    String name = pieces[1].split (":")[1];  // Strip off "ASV"
                    record.set (name, "$metadata", "dakota", "ASV", i);
                }
            }
            else
            {
                System.err.println ("Unrecognized format in parameter file.");
                System.exit (1);
            }
        }
        catch (IOException e)
        {
            System.err.println ("Can't read parameter file.");
            System.exit (1);
        }
    }

    /**
        Assumes this app was started solely for the purpose of running one specific job.
        This job operates outside the normal job management. The user is responsible
        for everything, including load balancing, directory and file management.
        Jobs can run remotely, but there is no support for retrieving results.
    **/
    public static void runHeadless (MNode record)
    {
        // See PanelEquations.launchJob()

        Path jobDir = Paths.get (System.getProperty ("user.dir")).toAbsolutePath ();  // Use current working directory, on assumption that's what the caller wants.
        String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ());  // This allows a remote job to run in the regular jobs directory there.
        MDoc job = new MDoc (jobDir.resolve ("job"), jobKey);  // Make this appear as if it is from the jobs collection.

        MPart collated = new MPart (record);
        NodeJob.collectJobParameters (collated, collated.get ("$inherit"), job);
        NodeJob.saveCollatedModel (collated, job);

        // Handle remote host
        Host host = Host.get (job);  // If a remote host is used, it must be specified exactly, rather than a list of possibilities.
        if (host instanceof Remote)  // Need to note the key so user can easily find the remote job directory.
        {
            job.set (jobKey, "remoteKey");
            job.save ();
        }

        // Start the job.
        Backend backend = Backend.getBackend (job.get ("backend"));
        backend.start (job);

        // Wait for completion
        NodeJob node = new NodeJobHeadless (job);
        while (node.complete < 1) node.monitorProgress ();

        // Convert to CSV, if requested.
        if (record.getFlag ("$metadata", "csv"))
        {
            Table table = new Table (jobDir.resolve ("out"), false);
            try {table.dumpCSV (jobDir.resolve ("out.csv"));}
            catch (IOException e) {}
        }

        // Extract results requested in ASV
        MNode ASV = record.child ("$metadata", "dakota", "ASV");
        if (ASV == null) return;  // nothing more to do
        OutputParser output = new OutputParser ();
        output.parse (jobDir.resolve ("out"));
        try (BufferedWriter writer = Files.newBufferedWriter (jobDir.resolve ("results")))
        {
            for (MNode o : ASV)
            {
                String name = o.get ();
                Column c = output.getColumn (name);
                float value = 0;
                if (c != null  &&  ! c.values.isEmpty ()) value = c.values.get (c.values.size () - 1);
                writer.write (value + " " + name);
            }
        }
        catch (IOException e) {}
    }

    @SuppressWarnings("serial")
    public static class NodeJobHeadless extends NodeJob
    {
        protected MNode source;

        public NodeJobHeadless (MNode source)
        {
            super (source, true);
            this.source = source;
        }

        public MNode getSource ()
        {
            return source;
        }
    }

    /**
        Run a study from the command line.
        Unlike runHeadless(), this function uses all the usual job management machinery.
    **/
    public static void studyHeadless (MNode record)
    {
        MPart collated = new MPart (record);
        if (! collated.containsKey ("study")) return;

        // See PanelRun constructor / prepare host monitor thread 
        Host.restartAssignmentThread ();
        for (Host h : Host.getHosts ()) h.restartMonitorThread ();

        MNode studyNode = PanelEquations.createStudy (collated.get ("$inherit"), collated);
        Study study = new Study (studyNode); // constructed in paused state
        study.togglePause ();                // start
        study.waitForCompletion ();

        // Output CSV files, if requested.
        if (record.getFlag ("$metadata", "csv"))
        {
            Path studyDir = study.getDir ();
            try (BufferedWriter parms = Files.newBufferedWriter (studyDir.resolve ("study.csv")))
            {
                SampleTableModel samples = new SampleTableModel ();
                samples.update (study);
                int rows = samples.getRowCount ();
                int cols = samples.getColumnCount ();
                int lastCol = cols - 1;

                // Header for study.csv file
                for (int c = 1; c < cols; c++)  // first column is job status, so skip it
                {
                    parms.write (samples.getColumnName (c));
                    if (c < lastCol) parms.write (",");
                }
                parms.newLine ();

                // Rows for study.csv file, along with converted output of each job.
                for (int r = 0; r < rows; r++)
                {
                    for (int c = 1; c < cols; c++)
                    {
                        parms.write (samples.getValueAt (r, c).toString ());
                        if (c < lastCol) parms.write (",");
                    }
                    parms.newLine ();

                    NodeJob jobNode = study.getJob (r);
                    Path jobDir = Host.getJobDir (Host.getLocalResourceDir (), jobNode.getSource ());
                    try
                    {
                        Table table = new Table (jobDir.resolve ("out"), false);
                        table.dumpCSV (studyDir.resolve (r + ".csv"));
                    }
                    catch (IOException e) {}
                }
            }
            catch (IOException e)
            {
                e.printStackTrace ();
            }
        }

        // See MainFrame window close listener
        AppData.quit (); // Save any modified data, particularly the study record.
        Host.quit ();    // Close down any ssh sessions.
    }

    public static void setUncaughtExceptionHandler (final JFrame parent)
    {
        Thread.setDefaultUncaughtExceptionHandler (new UncaughtExceptionHandler ()
        {
            public void uncaughtException (Thread thread, final Throwable throwable)
            {
                Path crashdump = Paths.get (AppData.properties.get ("resourceDir"), "crashdump");
                try
                {
                    PrintStream err = new PrintStream (crashdump.toFile ());
                    throwable.printStackTrace (err);
                    err.close ();
                }
                catch (FileNotFoundException e) {}

                JOptionPane.showMessageDialog
                (
                    parent,
                    "<html><body><p style='width:300px'>"
                    + "You've exposed a bug in the program! Please help us by filing a report on github. Describe what you were doing at the time, and include the file "
                    + crashdump.toAbsolutePath ()
                    + "</p></body></html>",
                    "Internal Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
