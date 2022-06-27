/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
    Wraps access to a system that runs jobs via IBM LSF
**/
public class RemoteLSF extends RemoteUnix
{
    public static Factory factory ()
    {
        return new FactoryRemote ()
        {
            public String className ()
            {
                return "RemoteLSF";
            }

            public Host createInstance ()
            {
                return new RemoteLSF ();
            }
        };
    }

    @Override
    public JPanel getEditor ()
    {
        if (panel == null)
        {
            panel = new EditorPanel2 ();
            panel.arrange ();
        }
        return panel;
    }

    @SuppressWarnings("serial")
    public class EditorPanel2 extends EditorPanel
    {
        public MTextField fieldProject = new MTextField (config, "project");
        public MTextField fieldMaxTime = new MTextField (config, "maxTime", "1d");

        public void arrange ()
        {
            Lay.BLtg (this, "N",
                Lay.BxL ("V",
                    Lay.FL (new JLabel ("Address"), fieldAddress),
                    Lay.FL (new JLabel ("Username"), fieldUsername),
                    Lay.FL (new JLabel ("Password"), fieldPassword),
                    Lay.FL (Box.createHorizontalStrut (30), labelWarning),
                    Lay.FL (new JLabel ("Home Directory"), fieldHome),
                    Lay.FL (new JLabel ("Project"), fieldProject),
                    Lay.FL (new JLabel ("Max Job Time (as UCUM d, h or min)"), fieldMaxTime),
                    Lay.FL (new JLabel ("Timeout (seconds)"), fieldTimeout),
                    Lay.FL (new JLabel ("Max Channels"), fieldMaxChannels),
                    Lay.FL (buttonConnect, buttonRestart, buttonZombie),
                    Lay.FL (new JLabel ("Messages:")),
                    Lay.FL (textMessages)
                )
            );
        }
    }

    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
        if (pid == 0) return false;
        for (ProcessInfo proc : getActiveProcs ())
        {
            if (proc.pid == pid)
            {
                job.set (proc.state, "queue");
                // This version considers both pending and suspended as active states,
                // along with the obvious "RUN" state.
                if (! ("DONE|EXIT").contains (proc.state)) return true;
                return false;
            }
        }
        return false;
    }

    @Override
    public List<ProcessInfo> getActiveProcs () throws Exception
    {
        List<ProcessInfo> result = new ArrayList<ProcessInfo> ();
        try (AnyProcess proc = build ("bjobs -o 'id stat' -noheader").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                ProcessInfo info = new ProcessInfo ();

                String[] pieces = line.trim ().split (" ", 2);
                info.pid   = Long.valueOf (pieces[0]);
                info.state = pieces[1].trim ();
                
                result.add (info);
            }
        }
        return result;
    }

    @Override
    public void submitJob (MNode job, boolean out2err, List<List<String>> commands) throws Exception
    {
        int count = commands.size ();
        if (count == 0) throw new Exception ("submitJob was called without any commands");

        Path resourceDir = getResourceDir ();  // implies connect()
        Path jobsDir     = resourceDir.resolve ("jobs");
        Path jobDir      = jobsDir.resolve (job.key ());
        Path scriptFile  = jobDir.resolve ("n2a_job.lsf");

        String inherit     = job.get ("$inherit").replaceAll (" ", "_");
        String project     = config.get ("project");
        String maxWalltime = config.getOrDefault ("1d", "maxTime");
        String walltime    = job.getOrDefault (maxWalltime, "host", "time");
        int    nodes       = job.getOrDefault (1,           "host", "nodes");
        int    cores       = job.getOrDefault (1,           "host", "cores");
        int    gpus        = job.getOrDefault (0,           "host", "gpus");

        Duration d = Duration.ofSeconds ((long) new UnitValue (walltime).get ());
        long h = d.toHours ();
        int  m = d.toMinutesPart ();

        try (BufferedWriter writer = Files.newBufferedWriter (scriptFile))
        {
            writer.write ("#!/bin/bash\n");
            writer.write ("#BSUB -P " + project + "\n");
            writer.write ("#BSUB -W " + h + ":" + m + "\n");
            writer.write ("#BSUB -nnodes " + nodes + "\n");
            writer.write ("#BSUB -J " + inherit + "\n");
            writer.write ("#BSUB -o " + (out2err ? "err" : "out") + "\n");
            if (! out2err) writer.write ("#BSUB -e err\n");  // without this, stderr goes to same file as "-o" above
            writer.write ("#BSUB -cwd " + quote (jobDir) + "\n");
            writer.write ("#BSUB -outdir " + quote (jobDir) + "\n");
            writer.write ("\n");

            writer.write ("jsrun -n " + nodes + " -a " + cores + " -c " + cores + " -g " + gpus + " " + combine (commands.get (0)) + "\n");
            for (int i = 1; i < count; i++)
            {
                // TODO: need a way to determine ranks per resource set. Right now it's just one per core.
                writer.write ("[ $? -eq 0 ] && jsrun -n " + nodes + " -a " + cores + " -c " + cores + " -g " + gpus + " " + combine (commands.get (i)) + "\n");
            }
            writer.write ("\n");

            writer.append ("if [ $? -eq 0 ]; then\n");  // Wait for process to finish.
            writer.append ("  echo success > finished\n");
            writer.append ("else\n");
            writer.append ("  echo failure > finished\n");
            writer.append ("fi\n");
        }

        try (AnyProcess proc = build ("bsub", quote (scriptFile)).start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            // Example output of bsub:
            //   Job <1994795> is submitted to default queue <batch>.
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (! line.startsWith ("Job <")) continue;
                line = line.substring (5).split (">", 2)[0];
                job.set (Long.parseLong (line), "pid");
                return;
            }

            // Failed to enqueue the job
            String stdErr = streamToString (proc.getErrorStream ());
            throw new Backend.AbortRun ("Could not start process:\n" + stdErr);
        }
    }

    public boolean clobbersOut ()
    {
        return true;
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
        if (pid == 0) return;

        try (AnyProcess proc = build ("bkill", force ? "" : "-s SIGTERM", String.valueOf (pid)).start ())
        {
            proc.wait ();  // To avoid killing the bkill process by closing the channel.
        }
    }

    // Load management is handled by LSF, so the following functions lie about resources
    // in order to encourage maximal loading. If throttling turns out to be necessary,
    // these can be modified to produce more useful numbers.

    @Override
    public long getMemoryTotal ()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMemoryFree ()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public int getProcessorTotal ()
    {
        // Return the maximum number of jobs allowed to wait in queue.
        // Jobs that are already running won't count against this.
        return 3;
    }

    @Override
    public double getProcessorIdle ()
    {
        int waiting = 0;
        try
        {
            for (ProcessInfo info : getActiveProcs ())
            {
                // This ignores suspended jobs. We only limit the number of pending jobs.
                if (info.state.equals ("PEND")) waiting++;
            }
        }
        catch (Exception e) {}
        return Math.max (0, getProcessorTotal () - waiting);
    }
}
