/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Export;
import gov.sandia.n2a.plugins.extpoints.ExportModel;
import gov.sandia.n2a.plugins.extpoints.ShutdownHook;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
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
import java.util.List;
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
        String format = null;
        Path path = null;
        for (String arg : args)
        {
            if      (arg.startsWith ("-plugin="    )) pluginClassNames.add            (arg.substring (8));
            else if (arg.startsWith ("-pluginDir=" )) pluginDirs      .add (Paths.get (arg.substring (11)).toAbsolutePath ());
            else if (arg.startsWith ("-param="     )) processParamFile (arg.substring (7), record);
            else if (arg.equals     ("-install"    )) headless = "install";
            else if (arg.equals     ("-csv"        )) record.set (true, "$meta", "csv");
            else if (arg.equals     ("-run"        )) headless = "run";
            else if (arg.equals     ("-study"      )) headless = "study";
            else if (arg.startsWith ("-export"     )) headless = "export";
            else if (arg.startsWith ("-import"     )) headless = "import";
            else if (arg.startsWith ("-model="     ))
            {
                MNode temp = new MVolatile ("", arg.substring (7));
                temp.merge (record);
                record = temp;
            }
            else if (arg.startsWith ("-file="))
            {
                path = Paths.get (arg.substring (6));
            }
            else if (arg.startsWith ("-format="))
            {
                format = arg.substring (8);
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
        AppData.properties.set ("1.2",                   "version");
        AppData.properties.set (! headless.isEmpty (),   "headless");
        AppData.checkInitialDB ();
        Path resourceDir = Paths.get (AppData.properties.get ("resourceDir"));

        // Get rid of eye-stab HiDPI scaling in recent JDKs. Instead, we handle DPI in SettingsLookAndFeel.
        // Must be done before any code activates Swing.
        System.setProperty ("sun.java2d.uiScale", "1.0");

        // Load plugins
        Path pluginDir = resourceDir.resolve ("plugins").toAbsolutePath ();
        try {Files.createDirectories (pluginDir);}
        catch (IOException e) {}
        pluginDirs.add (pluginDir);
        PluginManager.initialize (new N2APlugin (), pluginClassNames, pluginDirs);

        if (! headless.isEmpty ())
        {
            int exitCode = 0;
            switch (headless)
            {
                case "run":
                    exitCode = runHeadless (record);
                    break;
                case "study":
                    exitCode = studyHeadless (record);
                    break;
                case "import":
                    String name = record.key ();
                    if (name.isBlank ()) name = null;
                    exitCode = importHeadless (path, format, name);
                    break;
                case "export":
                    exitCode = exportHeadless (record, format, path);
                    break;
                case "install":
                    try
                    {
                        JobC jobC = new JobC (new MVolatile ());
                        jobC.runtimeDir = resourceDir.resolve ("backend").resolve ("c");
                        jobC.unpackRuntime ();

                        JobPython jobPython = new JobPython ();
                        jobPython.runtimeDir = resourceDir.resolve ("backend").resolve ("python");
                        jobPython.unpackRuntime ();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace ();
                        System.err.println ("Failed to unpack runtime resources.");
                        exitCode = 1;
                    }
                    break;
            }

            // Save all data and exit.
            // See MainFrame window close listener
            AppData.quit ();
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (ShutdownHook.class);
            for (ExtensionPoint exp : exps) ((ShutdownHook) exp).shutdown ();
            Host.quit ();  // Close down any ssh sessions.
            if (exitCode != 0) System.exit (exitCode);
            return;
        }

        // Create the main frame.
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                SettingsLookAndFeel.rescaling = true;
                SettingsLookAndFeel.instance.load ();  // Note that SettingsLookAndFeel is instantiated when the plugin system is initialized above.
                new MainFrame ();
                SettingsLookAndFeel.rescaling = false;
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
                record.set ("", "$meta", "dakota");  // Existence of key indicates that a Dakota-style response is requested.

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
                    record.set (name, "$meta", "dakota", "ASV", i);
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
    public static int runHeadless (MNode record)
    {
        // See PanelEquations.launchJob()

        Path jobDir = Paths.get (System.getProperty ("user.dir")).toAbsolutePath ();  // Use current working directory, on assumption that's what the caller wants.
        String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ());  // This allows a remote job to run in the regular jobs directory there.
        MDoc job = new MDoc (jobDir.resolve ("job"), jobKey);  // Make this appear as if it is from the jobs collection.

        String key = record.key ();
        MNode doc = AppData.docs.child ("models", key);
        if (doc == null)
        {
            System.err.println ("Model does not exit");
            return 1;
        }
        record.mergeUnder (doc);
        MPart collated = new MPart (record);  // TODO: the only reason to collate here is to ensure that host and backend are correctly identified if they are inherited. Need a more efficient method, such as lazy collation in MPart.
        NodeJob.collectJobParameters (collated, key, job);
        NodeJob.saveSnapshot (record, job);

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
        if (record.getFlag ("$meta", "csv"))
        {
            Table table = new Table (jobDir.resolve ("out"), false);
            try {table.dumpCSV (jobDir.resolve ("out.csv"));}
            catch (IOException e) {}
        }

        // Extract results requested in ASV
        MNode ASV = record.child ("$meta", "dakota", "ASV");
        if (ASV == null) return 0;  // nothing more to do
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

        return 0;
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
    public static int studyHeadless (MNode record)
    {
        String key = record.key ();
        MNode doc = AppData.docs.child ("models", key);
        if (doc == null)
        {
            System.err.println ("Model does not exist");
            return 1;
        }
        record.mergeUnder (doc);
        MPart collated = new MPart (record);
        if (! collated.containsKey ("study"))
        {
            System.err.println ("Model not tagged as a study");
            return 1;
        }

        // Start host monitor threads (see PanelRun constructor for GUI procedure)
        Host.restartAssignmentThread ();
        for (Host h : Host.getHosts ()) h.restartMonitorThread ();

        MNode studyNode = PanelEquations.createStudy (collated);
        Study study = new Study (studyNode); // constructed in paused state
        study.togglePause ();                // start
        study.waitForCompletion ();

        // Output CSV files, if requested.
        if (! record.getFlag ("$meta", "csv")) return 0;
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
            System.err.println ("Conversion to CSV failed");
            e.printStackTrace ();
            return 1;
        }

        return 0;
    }

    public static int importHeadless (Path path, String format, String name)
    {
        if (path == null)
        {
            System.err.println ("Import requires at least the path to the source file.");
            return 1;
        }
        if (! path.isAbsolute ()) path = Paths.get (System.getProperty ("user.dir")).resolve (path);

        try
        {
            PanelModel.importFile (path, format, name);
        }
        catch (Exception e)
        {
            System.err.println ("Import failed");
            e.printStackTrace();
            return 1;
        }

        return 0;
    }

    public static int exportHeadless (MNode record, String format, Path path)
    {
        // See PanelEquations.listenerExport(). The code here is highly stripped down because we're not dealing with a GUI.
        String key = record.key ();
        MNode doc = AppData.docs.child ("models", key);
        if (doc == null)
        {
            System.err.println ("Model does not exist");
            return 1;
        }
        record.mergeUnder (doc);

        if (format == null) format = "";
        if (path == null)
        {
            path = Paths.get (System.getProperty ("user.dir"));
            path = path.resolve (key);  // The exporter will add an appropriate suffix to this.
        }
        else if (! path.isAbsolute ())
        {
            path = Paths.get (System.getProperty ("user.dir")).resolve (path);
        }

        Export exporter = null;
        Export n2a      = null;
        List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Export.class);
        for (ExtensionPoint exp : exps)
        {
            if (! (exp instanceof ExportModel)) continue;
            Export em = ((ExportModel) exp);
            String name = em.getName ();
            if (name.contains ("N2A")) n2a = em;
            if (name.equalsIgnoreCase (format))         exporter = em;
            if (exporter == null  &&  em.accept (path)) exporter = em;  // Suffix match
        }
        if (exporter == null) exporter = n2a;
        if (exporter == null)
        {
            System.err.println ("No matching export method");
            return 1;
        }

        try
        {
            Files.createDirectories (path.getParent ());
            exporter.process (record, path);
        }
        catch (Exception e)
        {
            System.err.println ("Export failed");
            e.printStackTrace ();
            return 1;
        }

        return 0;
    }

    public static void setUncaughtExceptionHandler (final JFrame parent)
    {
        Thread.setDefaultUncaughtExceptionHandler (new UncaughtExceptionHandler ()
        {
            public void uncaughtException (Thread thread, final Throwable throwable)
            {
                if (throwable instanceof ThreadDeath) return;  // Caused by Thread.stop(), which only occurs in InternalBackend.kill(). Should be ignored.

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
