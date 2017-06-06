/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

public class Windows extends LocalMachineEnv
{
    @Override
    public Set<Long> getActiveProcs () throws Exception
    {
        Set<Long> result = new TreeSet<Long> ();
        String[] cmdArray = new String[] {"tasklist", "/v", "/fo", "table"};
        Process proc = Runtime.getRuntime ().exec (cmdArray);
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.toLowerCase ();
                if (line.contains ("n2a_job"))
                {
                    // If need to use /b for background and don't get title info anymore
                    String[] parts = line.split ("\\s+");
                    result.add (new Long (parts[1]));
                }
            }
        }
        return result;
    }

    @Override
    public long submitJob (MNode job, String command) throws Exception
    {
        String jobDir = new File (job.get ()).getParent ();

        File out = new File (jobDir, "out");
        File err = new File (jobDir, "err");

        File script = new File (jobDir, "n2a_job.bat");
        File finished = new File (jobDir, "finished");
        stringToFile (script, command + " > " + quotePath (out.getAbsolutePath ()) + " 2>> " + quotePath (err.getAbsolutePath ()) + "\r\ntype nul >> " + quotePath (finished.getAbsolutePath()) + "\r\n");
        String [] commandParm = new String[] {"cmd", "/c", "start", "/b", script.getAbsolutePath ()};
        Process p = Runtime.getRuntime ().exec (commandParm);
        p.waitFor ();
        if (p.exitValue () != 0) throw new Exception ("Failed to run job:\n" + streamToString (p.getErrorStream ()));

        return 0;  // TODO: Get PID of newly started job
    }

    @Override
    public void killJob (long pid) throws Exception
    {
        Runtime.getRuntime ().exec (new String [] {"taskkill", "/PID", String.valueOf (pid), "/F"});
    }

    @Override
    public String quotePath (String path)
    {
        return "\"" + path + "\"";
    }

    @Override
    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name"))
        {
            return "Windows";
        }
        return super.getNamedValue (name, defaultValue);
    }

	@Override
	public long getProcMem (long pid)
	{
		return 0;
	}
}
