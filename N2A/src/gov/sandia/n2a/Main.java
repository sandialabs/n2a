/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.jobs.OutputParser;
import gov.sandia.n2a.ui.jobs.OutputParser.Column;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JOptionPane;


public class Main
{
    public static void main (String[] args)
    {
        // Parse command line
        ArrayList<String> pluginClassNames = new ArrayList<String> ();
        ArrayList<File>   pluginDirs       = new ArrayList<File> ();
        MNode runModel = new MVolatile ();
        boolean headless = false;
        for (String arg : args)
        {
            if (arg.startsWith ("-plugin="   )) pluginClassNames.add           (arg.substring (8));
            if (arg.startsWith ("-pluginDir=")) pluginDirs      .add (new File (arg.substring (11)));

            if (arg.startsWith ("-run="))
            {
                runModel.set (arg.substring (5), "$inherit");
                headless = true;
            }
            if (arg.startsWith ("-param=")) processParamFile (arg.substring (7), runModel);
            if (! arg.startsWith ("-"))
            {
                String[] pieces = arg.split ("=", 2);
                String keys = pieces[0];
                String value = "";
                if (pieces.length == 2) value = pieces[1];
                runModel.set (value, keys.split ("\\."));
            }
        }

        if (! headless) setUncaughtExceptionHandler (null);

        // Set global application properties.
        AppData.properties.set ("Neurons to Algorithms", "name");
        AppData.properties.set ("N2A",                   "abbreviation");
        AppData.properties.set ("1.1",                   "version");
        AppData.checkInitialDB ();

        // Load plugins
        pluginClassNames.add ("gov.sandia.n2a.backend.internal.InternalPlugin");
        pluginClassNames.add ("gov.sandia.n2a.backend.xyce.XycePlugin");
        pluginClassNames.add ("gov.sandia.n2a.backend.c.PluginC");
        pluginClassNames.add ("gov.sandia.n2a.backend.neuroml.PluginNeuroML");
        pluginClassNames.add ("gov.sandia.n2a.backend.neuron.PluginNeuron");
        pluginDirs.add (new File (AppData.properties.get ("resourceDir"), "plugins"));
        if (! PluginManager.initialize (new N2APlugin (), pluginClassNames, pluginDirs))
        {
            System.err.println (PluginManager.getInitializationErrors ());
        }

        if (headless)
        {
            runHeadless (runModel);
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

    public static void processParamFile (String fileName, MNode runModel)
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
                Schema.readAll (runModel, reader);
            }
            else if (line.contains ("variables"))  // Dakota
            {
                runModel.set ("", "$metadata", "dakota");  // Existence of key indicates that a Dakota-style response is requested.

                // Variables
                int count = Integer.parseUnsignedInt (line.trim ().split ("\\s+")[0]);
                for (int i = 0; i < count; i++)
                {
                    line = reader.readLine ();
                    if (line == null) throw new IOException ();
                    String[] pieces = line.trim ().split ("\\s+");
                    runModel.set (pieces[0], pieces[1].split ("\\."));
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
                    runModel.set (name, "$metadata", "dakota", "ASV", i);
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

    public static void runHeadless (MNode runModel)
    {
        MPart collated = new MPart (runModel);
        MNode job = new MVolatile ();
        job.merge (collated);  // Avoid passing an actual MPart on to backend
        Path jobDir = Paths.get (System.getProperty ("user.dir")).toAbsolutePath ();
        job.set (jobDir.resolve ("model").toString ());  // Make job look like an MDoc, so backend can fetch working directory.
        String simulatorName = job.get ("$metadata", "backend");
        Backend backend = Backend.getBackend (simulatorName);
        backend.start (job);

        // Wait for completion
        // TODO: This duplicates some functionality in NodeJob.monitorProgress. Should really be part of a job management interface, as it can vary with host-backend combination.
        HostSystem env = HostSystem.get (job.get ("$metadata", "host"));
        Path finished = jobDir.resolve ("finished");
        long lastLiveCheck = System.currentTimeMillis ();
        while (true)
        {
            if (Files.exists (finished)) break;
            long currentTime = System.currentTimeMillis ();
            if (currentTime - lastLiveCheck > 10000)  // about 10 seconds
            {
                try
                {
                    if (env.isActive (job)) break;
                }
                catch (Exception e) {}
                lastLiveCheck = currentTime;
            }
        }

        // Extract results requested in ASV
        MNode ASV = job.child ("$metadata", "dakota", "ASV");
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

    public static void setUncaughtExceptionHandler (final JFrame parent)
    {
        Thread.setDefaultUncaughtExceptionHandler (new UncaughtExceptionHandler ()
        {
            public void uncaughtException (Thread thread, final Throwable throwable)
            {
                File crashdump = new File (AppData.properties.get ("resourceDir"), "crashdump");
                try
                {
                    PrintStream err = new PrintStream (crashdump);
                    throwable.printStackTrace (err);
                    err.close ();
                }
                catch (FileNotFoundException e) {}

                JOptionPane.showMessageDialog
                (
                    parent,
                    "<html><body><p style='width:300px'>"
                    + "You've exposed a bug in the program! Please help us by filing a report on github. Describe what you were doing at the time, and include the file "
                    + crashdump.getAbsolutePath ()
                    + "</p></body></html>",
                    "Internal Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
