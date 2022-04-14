/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

        public void arrange ()
        {
            Lay.BLtg (this, "N",
                Lay.BxL ("V",
                    Lay.FL (new JLabel ("Address"), fieldAddress),
                    Lay.FL (new JLabel ("Username"), fieldUsername),
                    Lay.FL (new JLabel ("Password"), fieldPassword),
                    Lay.FL (labelWarning),
                    Lay.FL (new JLabel ("Home Directory"), fieldHome),
                    Lay.FL (new JLabel ("Project"), fieldProject),
                    Lay.FL (panelRelays),
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
            // This version considers both pending and suspended as active states,
            // along with the obvious "RUN" state.
            if (proc.pid == pid  &&  ! ("DONE|EXIT").contains (proc.state)) return true;
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
        Path resourceDir = getResourceDir ();  // implies connect()
        Path jobsDir     = resourceDir.resolve ("jobs");
        Path jobDir      = jobsDir.resolve (job.key ());

        String inherit = job.get ("$inherit");
        String project = config.get ("project");
        int    nodes   = job.getOrDefault (1, "host", "nodes");
        int    cores   = job.getOrDefault (1, "host", "cores");
        int    gpus    = job.getOrDefault (0, "host", "gpus");
        String out     = quote (jobDir.resolve ("out"));
        String err     = quote (jobDir.resolve ("err"));

        try (BufferedWriter writer = Files.newBufferedWriter (jobDir.resolve ("n2a_job")))
        {
            writer.write ("#!/bin/bash\n");
            writer.write ("#BSUB -P " + project + "\n");
            writer.write ("#BSUB -W 24:0\n");
            writer.write ("#BSUB -nnodes " + nodes + "\n");
            writer.write ("#BSUB -J " + inherit + "\n");
            writer.write ("#BSUB -o " + (out2err ? err : out) + "\n");
            if (! out2err) writer.write ("#BSUB -e " + err + "\n");  // without this, stderr goes to same file as "-o" above
            writer.write ("\n");
            for (List<String> command : commands)
            {
                // TODO: need a way to determine ranks per resource set. Right now it's just one per core.
                writer.write ("jsrun -n " + nodes + " -a " + cores + " -c " + cores + " -g " + gpus + " " + combine (command) + "\n");
            }
        }

        try (AnyProcess proc = build ("bsub", quote (jobDir.resolve ("n2a_job"))).start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            // Example output of bsub:
            // Job <1994795> is submitted to queue <debug>.
            while (reader.ready ())
            {
                String line = reader.readLine ();
                if (! line.startsWith ("Job <")) continue;
                line = line.substring (5).split (">", 2)[0];
                job.set (Long.parseLong (line), "pid");
                return;
            }

            // Failed to enqueue the job
            String stdErr = streamToString (proc.getErrorStream ());
            Backend.err.get ().println ("Could not start process: " + stdErr);
            throw new Backend.AbortRun ();
        }
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
        if (pid == 0) return;

        try (AnyProcess proc = build ("bkill", force ? "" : "-s SIGTERM", String.valueOf (pid)).start ())
        {
            proc.wait ();  // To avoid killing the process by closing the channel.
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
