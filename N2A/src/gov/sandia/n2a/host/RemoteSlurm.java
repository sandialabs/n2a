/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
    Wraps access to a system that runs jobs via slurm.
**/
public class RemoteSlurm extends RemoteUnix
{
    public static Factory factory ()
    {
        return new FactoryRemote ()
        {
            public String className ()
            {
                return "RemoteSlurm";
            }

            public Host createInstance ()
            {
                return new RemoteSlurm ();
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
        public MTextField fieldAccount = new MTextField (config, "account");
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
                    Lay.FL (new JLabel ("Slurm Account"), fieldAccount),
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
    public boolean isAlive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
        if (pid == 0) return false;
        for (ProcessInfo proc : getActiveProcs ())
        {
            if (proc.pid == pid)
            {
                job.set (proc.state, "queue");
                // TODO: add other states that indicate job is still live
                if (("PENDING|RUNNING").contains (proc.state)) return true;
                return false;
            }
        }
        // If connected, then presumably we succeeded at checking for the process,
        // so we know it is dead (return false). However, if not connected, we
        // don't really know state of the process, so claim it is still alive
        // (return true). This prevents the job from going into dead state merely
        // due to lack of communication.
        return ! isConnected ();
    }

    @Override
    public List<ProcessInfo> getActiveProcs () throws Exception
    {
        List<ProcessInfo> result = new ArrayList<ProcessInfo> ();
        try (AnyProcess proc = build ("squeue -O JobID,State --noheader -u " + connection.username).start ();
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
        Path scriptFile  = jobDir.resolve ("n2a_job");

        String inherit = job.get ("$inherit");
        String account = config.get ("account");
        String maxTime = config.getOrDefault ("1d", "maxTime");
        String time    = job.getOrDefault (maxTime, "host", "time");  // time=0 indicates request infinite time; negative means don't specify time limit (use default for partition)
        int    nodes   = job.getOrDefault (1,       "host", "nodes");
        int    cores   = job.getOrDefault (1,       "host", "cores");
        String out     = quote (jobDir.resolve (out2err ? "err" : "out"));
        String err     = quote (jobDir.resolve ("err"));

        double duration = new UnitValue (time).get ();

        try (BufferedWriter writer = Files.newBufferedWriter (scriptFile))
        {
            writer.write ("#!/bin/bash -l\n");
            // Note: There may be other sbatch parameters that are worth controlling here.
            writer.write ("#SBATCH --nodes="     + nodes + "\n");
            if (duration >= 0) writer.write ("#SBATCH --time=" + (int) Math.ceil (duration / 60) + "\n");  // minutes. Can be > 59.
            writer.write ("#SBATCH --account="   + account + "\n");
            writer.write ("#SBATCH --job-name="  + inherit + "\n");
            writer.write ("#SBATCH --output="    + out + "\n");
            writer.write ("#SBATCH --error="     + err + "\n");
            writer.write ("\n");

            // TODO: Update command line with correct form for target HPC system.
            // TODO: need a way to determine ranks per resource set. Right now it's just one per core.
            writer.write ("mpiexec --npernode " + cores + " " + "numa_wrapper --ppn " + cores + " " + combine (commands.get (0)) + "\n");
            for (int i = 1; i < count; i++)
            {
                writer.write ("[ $? -eq 0 ] && mpiexec --npernode " + cores + " " + "numa_wrapper --ppn " + cores + " " + combine (commands.get (i)) + "\n");
            }
            writer.write ("\n");

            writer.append ("if [ $? -eq 0 ]; then\n");  // Wait for process to finish.
            writer.append ("  echo success > finished\n");
            writer.append ("else\n");
            writer.append ("  echo failure > finished\n");
            writer.append ("fi\n");
        }

        try (AnyProcess proc = build ("sbatch", quote (scriptFile)).start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            // Example output:
            //   Using wcid "FY139768"  found on CLI.
            //   Submitted batch job 10979768
            String line;
            while ((line = reader.readLine ()) != null)
            {
                String[] parts = line.split ("job", 2);
                if (parts.length == 2)
                {
                    job.set (Long.parseLong (parts[1].trim ()), "pid");
                    return;
                }
            }

            // Failed to enqueue the job
            String stdErr = streamToString (proc.getErrorStream ());
            throw new Backend.AbortRun ("Could not start process:\n" + stdErr);
        }
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
        if (pid == 0) return;

        try (AnyProcess proc = build ("scancel", force ? "" : "-s 15 ", String.valueOf (pid)).start ())
        {
            proc.wait ();  // To avoid killing the process by closing the channel.
        }
    }

    // Load management is handled by slurm, so the following functions lie about resources
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
        // Return the number of jobs currently waiting in queue.
        // For simplicity, count any job owned by the current user.
        int waiting = 0;
        try
        {
            for (ProcessInfo info : getActiveProcs ())
            {
                // TODO: determine what other states to include in "waiting".
                if (info.state.equals ("PENDING")) waiting++;
            }
        }
        catch (Exception e) {}
        return getProcessorTotal () - waiting;
    }
}
