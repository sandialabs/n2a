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
import java.util.Set;
import java.util.TreeSet;

public class RemoteParallel extends RemoteHost
{
    /**
        TODO: This code needs to be tested.
    **/
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
        String jobDir = job.get          (     "$metadata", "remote", "dir");
        String cores  = job.getOrDefault ("1", "$metadata", "cores");
        String nodes  = job.getOrDefault ("1", "$metadata", "remote", "nodes");

        setFileContents (jobDir + "/n2a_job",
            "#!/bin/bash\n"
            +  "mpiexec --npernode " + cores
            + " numa_wrapper --ppn " + cores
            + " " + command
        );

        // Note: There may be other sbatch parameters that are worth controlling here.
        Result r = Connection.exec
        (
            "sbatch"
            + " --nodes="            + nodes
            + " --time=24:00:00"
            + " --account="          + getNamedValue ("cluster.account", "FY139768")
            + " --job-name=N2A"
            + " --output='"          + jobDir + "/out'"
            + " --error='"           + jobDir + "/err'"
            + " '" + jobDir + "/n2a_job'"
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

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) return "RedSky (Parallel)";
        return super.getNamedValue (name, defaultValue);
    }

	@Override
	public long getProcMem (long pid)
	{
		// TODO Get process memory usage
		return 0;
	}
}
