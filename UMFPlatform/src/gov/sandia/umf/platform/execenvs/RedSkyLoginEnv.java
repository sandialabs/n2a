/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.execenvs;

import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.ssh.RedSkyConnection;
import gov.sandia.umf.platform.ssh.RedSkyConnection.Result;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;


public class RedSkyLoginEnv extends RedSkyEnv
{
    @Override
    public Set<Integer> getActiveProcs () throws Exception
    {
        Result r = RedSkyConnection.exec ("ps ux");
        if (r.error) throw new Exception (r.stdErr);
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));

        Set<Integer> result = new TreeSet<Integer> ();
        String line;
        while ((line = reader.readLine ()) != null)
        {
            if (line.contains ("ps ux")) continue;
            if (! line.contains ("model")) continue;
            line = line.trim ();
            String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
            result.add (new Integer (parts[1]));  // pid is second column
        }
        return result;
    }

    @Override
    public void submitJob (RunState run) throws Exception
    {
        String command = run.getNamedValue ("command");
        String jobDir  = run.getNamedValue ("jobDir");
        RedSkyConnection.exec (command + " > '" + jobDir + "/out' 2> '" + jobDir + "/err' &", true);
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) return "RedSky (Login)";
        return super.getNamedValue (name, defaultValue);
    }

    @Override
    public void submitBatch(RunEnsemble re) throws Exception {
        // TODO Auto-generated method stub
        
    }

	@Override
	public long getProcMem(Integer procNum) {
		// TODO Auto-generated method stub
		return 0;
	}
}
