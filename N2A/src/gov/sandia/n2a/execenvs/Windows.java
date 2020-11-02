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
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;

public class Windows extends HostSystem
{
    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return false;

        String jobDir = Paths.get (job.get ()).getParent ().toString ();
        Process proc = new ProcessBuilder ("powershell", "get-process", "-Id", String.valueOf (pid), "|", "format-table", "Path").start ();
        proc.getOutputStream ().close ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.startsWith (jobDir)) return true;
            }
        }
        return false;
    }

    @Override
    public Set<Long> getActiveProcs () throws Exception
    {
        Set<Long> result = new TreeSet<Long> ();

        Path   resourceDir = getResourceDir ();
        String jobsDir     = resourceDir.resolve ("jobs").toString ();

        Process proc = new ProcessBuilder ("powershell", "get-process", "|", "format-table", "Id,Path").start ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains (jobsDir))
                {
                    String[] parts = line.trim ().split (" ", 2);
                    result.add (new Long (parts[0]));
                }
            }
        }
        return result;
    }

    @Override
    public void submitJob (MNode job, String command) throws Exception
    {
        Path jobDir = Paths.get (job.get ()).getParent ();
        Path script = jobDir.resolve ("n2a_job.bat");
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

        Process proc = new ProcessBuilder ("cmd", "/c", "start", "/b", script.toString ()).start ();
        proc.waitFor ();
        if (proc.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to run job:\n" + streamToString (proc.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        // Get PID of newly-started job
        String jobDirString = jobDir.toString ();
        proc = new ProcessBuilder ("powershell", "get-process", "|", "format-table", "Id,Path").start ();
        proc.getOutputStream ().close ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains (jobDirString))
                {
                    line = line.trim ().split (" ", 2)[0];
                    job.set (Long.parseLong (line), "$metadata", "pid");
                    return;
                }
            }
        }
    }

    @Override
    public void killJob (long pid, boolean force) throws Exception
    {
        if (force) new ProcessBuilder ("taskkill", "/PID", String.valueOf (pid), "/F").start ();
        // Windows does not provide a simple way to signal a non-GUI process.
        // Instead, the program is responsible to poll for the existence of the "finished" file
        // on a reasonable interval, say once per second. See Backend.kill()
    }

    @Override
    public String quotePath (Path path)
    {
        return "\"" + path + "\"";
    }

	@Override
	public long getProcMem (long pid)
	{
		return 0;
	}
}
