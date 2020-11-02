/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.Connection.Result;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
    Wraps access to a system that runs jobs via slurm.
**/
public class RemoteParallel extends RemoteHost
{
    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return false;

        Result r = Connection.exec ("squeue -o \"%i\" -u " + System.getProperty ("user.name"));
        if (r.error  &&  r.stdErr != null  &&  ! r.stdErr.isEmpty ()) return false;

        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));
        String line;
        while ((line = reader.readLine ()) != null)
        {
            long pidListed = Long.parseLong (line);
            if (pidListed == pid) return true;
        }
        return false;
    }

    @Override
    public Set<Long> getActiveProcs() throws Exception
    {
        Result r = Connection.exec ("squeue -o \"%i\" -u " + System.getProperty ("user.name"));
        if (r.error && r.stdErr != null && !r.stdErr.equals (""))
        {
            Backend.err.get ().println (r.stdErr);
            throw new Backend.AbortRun ();
        }
        Set<Long> result = new TreeSet<Long> ();
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));
        String line;
        while((line = reader.readLine ()) != null)
        {
            result.add (new Long (line));
        }
        return result;
    }

    @Override
    public void submitJob (MNode job, String command) throws Exception
    {
        Path resourceDir = getResourceDir ();
        Path jobsDir     = resourceDir.resolve ("jobs");
        Path jobDir      = jobsDir.resolve (job.key ());

        String cores = job.getOrDefault ("1", "$metadata", "cores");
        String nodes = job.getOrDefault ("1", "$metadata", "remote", "nodes");

        stringToFile (jobDir.resolve ("n2a_job"),
            "#!/bin/bash\n"
            +  "mpiexec --npernode " + cores
            + " numa_wrapper --ppn " + cores
            + " " + command
        );

        // Note: There may be other sbatch parameters that are worth controlling here.
        Result r = Connection.exec
        (
            "sbatch"
            + " --nodes="           + nodes
            + " --time=24:00:00"
            + " --account="         + metadata.get ("cluster", "account")
            + " --job-name=N2A"
            + " --output="          + quotePath (jobDir.resolve ("out"))
            + " --error="           + quotePath (jobDir.resolve ("err"))
            + " " + quotePath (jobDir.resolve ("n2a_job"))
        );
        if (r.error)
        {
            Backend.err.get ().println ("Could not start process: " + r.stdErr);
            throw new Backend.AbortRun ();
        }

        // Example output:
        //   Using wcid "FY139768"  found on CLI.
        //   Submitted batch job 10979768
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));
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
    }

    @Override
    public void killJob (long pid, boolean force) throws Exception
    {
        Connection.exec ("scancel " + (force ? "" : "-s 15 ") + pid);
    }

    // Load management is handled by slurm, so the following functions lie about resources
    // in order to encourage maximal loading. If throttling turns out to be necessary,
    // these can be modified to produce more useful numbers.

    @Override
    public long getProcMem (long pid) throws Exception
    {
        return 0;
    }

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
        return Integer.MAX_VALUE;
    }

    @Override
    public double getProcessorLoad ()
    {
        return 0;
    }
}
