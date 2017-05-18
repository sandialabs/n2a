/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ssh.RedSkyConnection;
import gov.sandia.n2a.ssh.RedSkyConnection.Result;

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
    public void submitJob (MNode job, String command) throws Exception
    {
        String jobDir = job.get ("$metadata", "remote.dir");  // Of course, this is a dir this class generated for the job.
        RedSkyConnection.exec (command + " > '" + jobDir + "/out' 2> '" + jobDir + "/err' &", true);
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) return "RedSky (Login)";
        return super.getNamedValue (name, defaultValue);
    }

	@Override
	public long getProcMem(Integer procNum) {
		// TODO Auto-generated method stub
		return 0;
	}
}
