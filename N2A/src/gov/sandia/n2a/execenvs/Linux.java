/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Linux extends LocalHost
{
    static boolean writeBackgroundScript = true;  // always write the script on first use in a given session

    @Override
    public Set<Long> getActiveProcs () throws Exception
    {
        Set<Long> result = new TreeSet<Long> ();
        Process proc = new ProcessBuilder ("ps", "-eo", "pid,command").start ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.toLowerCase ();
                if (line.contains ("n2a_job"))
                {
                    line = line.trim ();
                    String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
                    result.add (Long.parseLong (parts[0]));
                }
            }
        }
        return result;
    }

    @Override
    public long getProcMem (long pid) throws Exception
    {
        String[] cmdArray = new String[] {"ps", "-p", String.valueOf (pid), "-o", "pid,rss"};
        Process proc = Runtime.getRuntime ().exec (cmdArray);
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            // first line is headers, second has info we want
            String line = reader.readLine ();
            if (line == null) return -1;
            line = reader.readLine ();
            if (line == null) return -1;

            line = line.trim ();
            String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
            return Long.parseLong(parts[1]) * 1024;
        }
    }

    @Override
    public void submitJob (MNode job, String command) throws Exception
    {
        File binDir     = new File (AppData.properties.get ("resourceDir"), "bin");
        File background = new File (binDir, "background");
        if (writeBackgroundScript)
        {
            writeBackgroundScript = false;
            binDir.mkdirs ();
            stringToFile
            (
                background,
                "#!/bin/bash\n"
                + "$1 &\n"
            );

            String [] commandParm = {"chmod", "u+x", background.getAbsolutePath ()};
            Process p = Runtime.getRuntime ().exec (commandParm);
            p.waitFor ();
            if (p.exitValue () != 0)
            {
                Backend.err.get ().println ("Failed to change permissions on background script:\n" + streamToString (p.getErrorStream ()));
                throw new Backend.AbortRun ();
            }
        }

        String jobDir = new File (job.get ()).getParent ();
        File script = new File (jobDir, "n2a_job");
        stringToFile
        (
            script,
            "#!/bin/bash\n"
            + "cd " + jobDir + "\n"
            + "if " + command + " > out 2>> err; then\n"   // removed "&" so we wait for process to finish, assuming we can background it directly with sh
            + "  echo success > finished\n"
            + "else\n"
            + "  echo failure > finished\n"
            + "fi"
        );

        String [] commandParm = {"chmod", "u+x", script.getAbsolutePath ()};
        Process p = Runtime.getRuntime ().exec (commandParm);
        p.waitFor ();
        if (p.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to change permissions on job script:\n" + streamToString (p.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        commandParm = new String[] {background.getAbsolutePath (), script.getAbsolutePath ()};
        p = Runtime.getRuntime ().exec (commandParm);
        p.waitFor ();
        if (p.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to run job:\n" + streamToString (p.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        // Get PID of newly created job
        // A command line containing the path to the job dir should uniquely identify the correct process.
        Process proc = Runtime.getRuntime ().exec (new String[] {"ps", "-ewwo", "pid,command"});
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains (jobDir))
                {
                    line = line.trim ();
                    String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
                    job.set (Long.parseLong (parts[0]), "$metadata", "pid");
                    return;
                }
            }
        }
    }

    @Override
    public void killJob (long pid) throws Exception
    {
        // Scan for PIDs chained from the given one. We need to kill them all.
        Set<Long> pids = new TreeSet<Long> ();
        pids.add (pid);
        Process proc = new ProcessBuilder ("ps", "-eo", "pid,ppid").start ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            System.out.println ("  in try");
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.trim ();
                String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
                try
                {
                    long PID  = Long.parseLong (parts[0]);
                    long PPID = Long.parseLong (parts[1]);
                    if (pids.contains (PPID)) pids.add (PID);
                }
                catch (Exception e) {}
            }
        }

        List<String> command = new ArrayList<String> ();
        command.add ("kill");
        command.add ("-9");
        for (long l : pids) command.add (String.valueOf (l));
        new ProcessBuilder (command).start ();
    }

    @Override
    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) return "Linux";
        if (name.equalsIgnoreCase ("xyce.binary"))
        {
            return "Xyce";
        }
        return super.getNamedValue (name, defaultValue);
    }
}
