/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Windows extends Host
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "Windows";
            }

            public boolean isRemote ()
            {
                return false;
            }

            public Host createInstance ()
            {
                return new Windows ();
            }
        };
    }

    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
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
    public List<ProcessInfo> getActiveProcs () throws Exception
    {
        List<ProcessInfo> result = new ArrayList<ProcessInfo> ();
        Map<Long,  ProcessInfo> id2process   = new HashMap<Long,  ProcessInfo> ();
        Map<String,ProcessInfo> name2process = new HashMap<String,ProcessInfo> ();

        Path   resourceDir = getResourceDir ();
        String jobsDir     = resourceDir.resolve ("jobs").toString ();

        Process proc = new ProcessBuilder ("powershell", "get-process", "|", "format-table", "Id,WorkingSet,Path").start ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains (jobsDir))
                {
                    ProcessInfo info = new ProcessInfo ();

                    String[] parts = line.trim ().split (" ", 2);
                    info.pid = Long.valueOf (parts[0]);

                    parts = parts[1].trim ().split (" ", 2);
                    info.memory = Long.valueOf (parts[0]);

                    result.add (info);
                    id2process.put (info.pid, info);
                }
            }
        }

        // Extra awkward work to get cpu utilization out of powershell
        proc = new ProcessBuilder ("powershell", "get-counter", "\"\\process(*)\\id process\",\"\\process(*)\\% processor time\"").start ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                String[] pieces = line.split ("process\\(", 2);
                if (pieces.length == 1) continue;

                pieces = pieces[1].split ("\\)", 2);
                String name = pieces[0];

                String line2 = reader.readLine ();

                if (line.contains ("id process"))
                {
                    long value = Long.valueOf (line2);
                    ProcessInfo info = id2process.get (value);
                    if (info != null)
                    {
                        name2process.put (name, info);
                    }
                }
                else if (line.contains ("processor time"))
                {
                    ProcessInfo info = name2process.get (name);
                    if (info != null) info.cpu = Double.valueOf (line2);
                }
            }
        }
        
        return result;
    }

    @Override
    public void submitJob (MNode job, String... command) throws Exception
    {
        Path jobDir = Paths.get (job.get ()).getParent ();
        Path script = jobDir.resolve ("n2a_job.bat");
        String combined = combine (command);
        stringToFile
        (
            script,
            "cd " + jobDir + "\r\n"
            + combined + " > out 2>> err\r\n"
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
                    job.set (Long.parseLong (line), "pid");
                    return;
                }
            }
        }
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        long pid = job.getOrDefault (0l, "pid");
        if (pid == 0) return;

        if (force) new ProcessBuilder ("taskkill", "/PID", String.valueOf (pid), "/F").start ();
        // Windows does not provide a simple way to signal a non-GUI process.
        // Instead, the program is responsible to poll for the existence of the "finished" file
        // on a reasonable interval, say once per second. See Backend.kill()
    }

    public void deleteTree (Path start)
    {
        // Hack to deal with file locks held by NIO.
        // See notes on Host.DeleteTreeVisitor ctor.
        System.gc ();

        if (Files.isDirectory (start))
        {
            try (AnyProcess proc = build ("cmd", "/c", "rd", "/q", "/s", quote (start)).start ()) {}
            catch (Exception e) {}
        }
        else  // single file
        {
            try (AnyProcess proc = build ("cmd", "/c", "del", "/q", "/f", quote (start)).start ()) {}
            catch (Exception e) {}
        }
    }

    @Override
    public String quote (Path path)
    {
        return "\"" + path + "\"";
    }
}
