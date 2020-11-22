/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
    Wraps access to a system that runs jobs via slurm.
**/
public class RemoteSlurm extends RemoteUnix
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "RemoteSlurm";
            }

            public boolean isRemote ()
            {
                return true;
            }

            public Host createInstance ()
            {
                return new RemoteSlurm ();
            }
        };
    }

    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return false;

        try (AnyProcess proc = build ("squeue -O JobID --noheader -u " + connection.username).start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                long pidListed = Long.parseLong (line);
                if (pidListed == pid) return true;
            }
        }
        return false;
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
    public void submitJob (MNode job, String command) throws Exception
    {
        Path resourceDir = getResourceDir ();  // implies connect()
        Path jobsDir     = resourceDir.resolve ("jobs");
        Path jobDir      = jobsDir.resolve (job.key ());

        String cores = job.getOrDefault ("1", "$metadata", "cores");
        String nodes = job.getOrDefault ("1", "$metadata", "remote", "nodes");

        stringToFile (jobDir.resolve ("n2a_job"),
              "#!/bin/bash\n"
            + "mpiexec --npernode " + cores + " " + "numa_wrapper --ppn " + cores + " " + command
        );

        // Note: There may be other sbatch parameters that are worth controlling here.
        try (AnyProcess proc = build (
                "sbatch",
                "--nodes="   + nodes,
                "--time=24:00:00",
                "--account=" + config.get ("cluster", "account"),
                "--job-name=N2A",  // TODO: use better job name here, one that is unique
                "--output="  + quote (jobDir.resolve ("out")),
                "--error="   + quote (jobDir.resolve ("err")),
                quote (jobDir.resolve ("n2a_job"))).start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            // Example output:
            //   Using wcid "FY139768"  found on CLI.
            //   Submitted batch job 10979768
            while (reader.ready ())
            {
                String line = reader.readLine ();
                String[] parts = line.split ("job", 2);
                if (parts.length == 2)
                {
                    job.set (Long.parseLong (parts[1].trim ()), "$metadata", "pid");
                    return;
                }
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
        long pid = job.getOrDefault (0l, "$metadata", "pid");
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
    public long getMemoryPhysicalTotal ()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMemoryPhysicalFree ()
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
    public double getProcessorLoad ()
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
        return waiting;
    }
}
