/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
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

        File script = new File (jobDir, "n2a_job.bat");
        stringToFile
        (
            script,
            "cd " + jobDir + "\r\n"
            + command + " > out 2>> err\r\n"
            + "if errorlevel 0 (\r\n"
            + "  echo success > finished\r\n"
            + ") else (\r\n"
            + "  echo failure > finished\r\n"
            + ")\r\n"
        );
        String [] commandParm = new String[] {"cmd", "/c", "start", "/b", script.getAbsolutePath ()};
        Process p = Runtime.getRuntime ().exec (commandParm);
        p.waitFor ();
        if (p.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to run job:\n" + streamToString (p.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        // Get PID of newly-started job
        Process proc = Runtime.getRuntime ().exec (new String[] {"tasklist", "/v", "/fo", "table"});  // TODO: might be safer to use CSV format instead
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.toLowerCase ();
                if (line.contains (jobDir))
                {
                    String[] parts = line.split ("\\s+");
                    return Long.parseLong (parts[1]);
                }
            }
        }
        return 0;
    }

    @Override
    public void killJob (long pid) throws Exception
    {
        Runtime.getRuntime ().exec (new String [] {"taskkill", "/PID", String.valueOf (pid), "/F"});
    }

    @Override
    public String quotePath (Path path)
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
