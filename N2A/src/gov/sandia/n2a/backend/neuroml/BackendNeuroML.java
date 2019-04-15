/*
Copyright 2018-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class BackendNeuroML extends Backend
{
    @Override
    public String getName ()
    {
        return "LEMS";  // This is the name of the target simulator. NeuroML is an interchange format, not a simulator.
    }

    @Override
    public void start (MNode job)
    {
        Thread simulationThread = new SimulationThread (job);
        simulationThread.setDaemon (true);
        simulationThread.start ();
    }

    @Override
    public void kill (MNode job)
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid != 0)
        {
            try
            {
                HostSystem.get (job.getOrDefault ("localhost", "$metadata", "host")).killJob (pid);
                Path jobDir = Paths.get (job.get ()).getParent ();
                Files.copy (new ByteArrayInputStream ("killed" .getBytes ("UTF-8")), jobDir.resolve ("finished"));
            }
            catch (Exception e) {}
        }
    }

    public class SimulationThread extends Thread
    {
        public MNode  job;
        public Path   jobDir;
        public Path   modelPath;
        public String jnmlHome;
        public Path   jnmlCommand;
        public String simulator = "";

        public SimulationThread (MNode job)
        {
            super ("NeuroML Simulation");
            this.job = job;
        }

        public void run ()
        {
            jobDir = Paths.get (job.get ()).getParent ();  // assumes the MNode "job" is really an MDoc. In any case, the value of the node should point to a file on disk where it is stored in a directory just for it.
            try {err.set (new PrintStream (new FileOutputStream (jobDir.resolve ("err").toFile (), true), false, "UTF-8"));}
            catch (Exception e) {}

            try
            {
                // Export the model to NeuroML
                Files.createFile (jobDir.resolve ("started"));
                String inherit = job.get ("$inherit").replace ("\"", "");
                MNode doc = AppData.models.child (inherit);
                modelPath = jobDir.resolve ("model.nml");
                ExportJob exportJob = PluginNeuroML.exporter.export (doc, modelPath, true);

                // Record metadata
                if (! exportJob.duration.isEmpty ()) job.set ("$metadata", "duration", exportJob.duration);
                if (exportJob.simulation != null)
                {
                    List<String> outputFiles = exportJob.simulation.dumpColumns (jobDir);
                    String defaultOutput = "";
                    for (String f : outputFiles)
                    {
                        if (defaultOutput.isEmpty ()  ||  f.startsWith ("defaultOutput")) defaultOutput = f;
                    }
                    if (! defaultOutput.isEmpty ()) job.set ("$metadata", "defaultOutput", defaultOutput);
                }

                // Convert the model to target format using jnml

                jnmlHome = AppData.state.get ("BackendNeuroML", "JNML_HOME");
                if (jnmlHome.isEmpty ()) jnmlHome = System.getenv ("JNML_HOME");  // This is unlikely to be set, but respect if it is.
                if (jnmlHome == null) jnmlHome = "/usr/local/jNeuroML";  // just a guess
                jnmlCommand = Paths.get (jnmlHome, "jnml");

                if (! simulator.isEmpty ()  &&  ! simulator.equals ("neuron"))  // NUERON gets special treatment because jnml will run it for us.
                {
                    ProcessBuilder b = new ProcessBuilder (jnmlCommand.toString (), modelPath.toString (), "-" + simulator);
                    Map<String,String> env = b.environment ();
                    env.put ("JNML_HOME", jnmlHome);

                    Path out = jobDir.resolve ("jnml.out");
                    Path err = jobDir.resolve ("jnml.err");
                    b.redirectOutput (out.toFile ());  // Should truncate existing files.
                    b.redirectError  (err.toFile ());

                    int result = 1;
                    try
                    {
                        Process p = b.start ();
                        p.waitFor ();
                        result = p.exitValue ();
                    }
                    catch (Exception e) {}

                    if (result != 0)
                    {
                        try
                        {
                            PrintStream os = Backend.err.get ();
                            os.println ("jnml failed:");
                            os.print (HostSystem.streamToString (Files.newInputStream (err)));
                        }
                        catch (Exception e) {}
                    }

                    try
                    {
                        Files.delete (out);
                        Files.delete (err);
                    }
                    catch (Exception e) {}

                    if (result != 0) throw new Backend.AbortRun ();
                }

                // Close error file so external simulator can append to it.
                PrintStream ps = Backend.err.get ();
                if (ps != System.err)
                {
                    ps.close ();
                    err.remove ();
                }

                // Run the model on the target simulator
                submitJob ();
            }
            catch (AbortRun a)
            {
            }
            catch (Exception e)
            {
                e.printStackTrace (Backend.err.get ());
            }

            // If an exception occurred, the error file could still be open.
            PrintStream ps = Backend.err.get ();
            if (ps != System.err) ps.close ();
        }

        public void submitJob () throws Exception
        {
            HostSystem env = HostSystem.get (job.getOrDefault ("localhost", "$metadata", "host"));
            String command = "JNML_HOME=" + jnmlHome + " " + env.quotePath (jnmlCommand) + " " + env.quotePath (modelPath) + " -nogui";
            env.submitJob (job, command);
        }
    }

    @Override
    public double currentSimTime (MNode job)
    {
        String defaultOutput = job.get ("$metadata", "defaultOutput");
        if (defaultOutput.isEmpty ()) return 0;

        Path jobDir = Paths.get (job.get ()).getParent ();
        Path out = jobDir.resolve (defaultOutput);
        return InternalBackend.getSimTimeFromOutput (out);
    }
}
