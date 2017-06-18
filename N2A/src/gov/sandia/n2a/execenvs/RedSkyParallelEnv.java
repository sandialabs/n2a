/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ssh.RedSkyConnection;
import gov.sandia.n2a.ssh.RedSkyConnection.Result;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;

public class RedSkyParallelEnv extends RedSkyEnv
{
    @Override
    public Set<Long> getActiveProcs() throws Exception
    {
        Result r = RedSkyConnection.exec ("squeue -o \"%u %i\" -u " + System.getProperty ("user.name"));
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
            result.add (new Long (line.substring (line.indexOf (" ") + 1)));
        }
        return result;
    }

    @Override
    public long submitJob (MNode job, String command) throws Exception
    {
        String jobDir = job.get          ("$metadata", "remote.dir");
        String cores  = job.getOrDefault ("$metadata", "cores",        "1");
        String nodes  = job.getOrDefault ("$metadata", "remote.nodes", "1");

        setFileContents (jobDir + "/n2a_job",
            "#!/bin/bash\n"
            +  "mpiexec --npernode " + cores
            + " numa_wrapper --ppn " + cores
            + " " + command
        );

        // Note: There may be other sbatch parameters that are worth controlling here.
        Result r = RedSkyConnection.exec
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
                return Long.parseLong (parts[1].trim ());
            }
        }

        return 0;
    }

    @Override
    public void killJob (long pid) throws Exception
    {
        RedSkyConnection.exec ("scancel " + pid);
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
