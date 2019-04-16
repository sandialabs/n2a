/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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


public class RemoteGateway extends RemoteHost
{
    @Override
    public Set<Long> getActiveProcs () throws Exception
    {
        Result r = Connection.exec ("ps ux");
        if (r.error)
        {
            Backend.err.get ().println (r.stdErr);
            throw new Backend.AbortRun ();
        }
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));

        Set<Long> result = new TreeSet<Long> ();
        String line;
        while ((line = reader.readLine ()) != null)
        {
            if (line.contains ("ps ux")) continue;
            if (! line.contains ("model")) continue;
            line = line.trim ();
            String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
            result.add (new Long (parts[1]));  // pid is second column
        }
        return result;
    }

    @Override
    public void submitJob (MNode job, String command) throws Exception
    {
        String jobDir = job.get ("$metadata", "remote", "dir");  // Of course, this is a dir this class generated for the job.
        Connection.exec (command + " > '" + jobDir + "/out' 2> '" + jobDir + "/err' &", true);

        // Get PID of newly-created job
        Result r = Connection.exec ("ps -ewwo pid,command");
        if (r.error) return;
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));
        String line;
        while ((line = reader.readLine ()) != null)
        {
            line = line.trim ();
            String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
            job.set (Long.parseLong (parts[0]), "$metadata", "pid");
            return;
        }
    }

    @Override
    public void killJob (long pid) throws Exception
    {
        Connection.exec ("kill -9 " + pid);
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) return "RedSky (Login)";
        return super.getNamedValue (name, defaultValue);
    }

	@Override
	public long getProcMem (long pid)
	{
	    // TODO: measure process memory usage
		return 0;
	}
}
