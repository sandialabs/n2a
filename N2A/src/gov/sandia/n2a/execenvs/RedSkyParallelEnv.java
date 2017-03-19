/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ssh.RedSkyConnection;
import gov.sandia.n2a.ssh.RedSkyConnection.Result;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;

import replete.util.User;

public class RedSkyParallelEnv extends RedSkyEnv
{
    @Override
    public Set<Integer> getActiveProcs() throws Exception
    {
        Result r = RedSkyConnection.exec ("squeue -o \"%u %i\" -u " + User.getName ());
        if (r.error && r.stdErr != null && !r.stdErr.equals (""))
        {
            throw new Exception (r.stdErr);
        }
        Set<Integer> result = new TreeSet<Integer> ();
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));
        String line;
        while((line = reader.readLine ()) != null)
        {
            result.add (new Integer (line.substring (line.indexOf (" ") + 1)));
        }
        return result;
    }

    @Override
    public void submitJob (MNode job, String command) throws Exception
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
        if (r.error) throw new Exception ("Could not start process: " + r.stdErr);

        // Part of the output of an sbatch launch is the job #.  This could be used
        // to track the job directly.  Example output:
        //   Using wcid "FY139768"  found on CLI.
        //   Submitted batch job 10979768
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) return "RedSky (Parallel)";
        return super.getNamedValue (name, defaultValue);
    }

	@Override
	public long getProcMem(Integer procNum) {
		// TODO Auto-generated method stub
		return 0;
	}
}
