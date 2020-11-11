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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Unix extends Host
{
    static boolean writeBackgroundScript = true;  // always write the script on first use in a given session

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "Unix";
            }

            public Host createInstance ()
            {
                return new Unix ();
            }
        };
    }

    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return false;

        String jobDir = Host.getJobDir (getResourceDir (), job).toAbsolutePath ().toString ();
        try (AnyProcess proc = build ("ps", "-q", String.valueOf (pid), "-wwo", "command", "--no-header").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
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
        String jobsDir     = resourceDir.resolve ("jobs").toAbsolutePath ().toString ();

        try (AnyProcess proc = build ("ps", "-ewwo", "pid,command", "--no-header").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
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
        Path resourceDir = getResourceDir ();
        Path binDir      = resourceDir.resolve ("bin");
        Path background  = binDir.resolve ("background");
        if (writeBackgroundScript)
        {
            writeBackgroundScript = false;
            Files.createDirectories (binDir);
            stringToFile (background,
                  "#!/bin/bash\n"
                + "$1 &\n"
            );
            Files.setPosixFilePermissions (background, PosixFilePermissions.fromString ("rwxr--r--"));
        }

        Path jobDir = Host.getJobDir (resourceDir, job);
        Path script = jobDir.resolve ("n2a_job");
        stringToFile (script,
              "#!/bin/bash\n"
            + "cd " + quote (jobDir) + "\n"
            + "if " + command + " > out 2>> err; then\n"   // removed "&" so we wait for process to finish, assuming we can background it directly with sh
            + "  echo success > finished\n"
            + "else\n"
            + "  echo failure > finished\n"
            + "fi"
        );
        Files.setPosixFilePermissions (script, PosixFilePermissions.fromString ("rwxr--r--"));

        try (AnyProcess proc = build (quote (background), quote (script)).start ();)
        {
            proc.waitFor ();
            if (proc.exitValue () != 0)
            {
                Backend.err.get ().println ("Failed to run job:\n" + streamToString (proc.getErrorStream ()));
                throw new Backend.AbortRun ();
            }
        }

        // Get PID of newly created job
        String jobDirString = jobDir.toString ();
        try (AnyProcess proc = build ("ps", "-ewwo", "pid,command", "--no-header").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains (jobDirString))
                {
                    line = line.trim ();
                    String[] parts = line.split ("\\s+");
                    job.set (Long.parseLong (parts[0]), "$metadata", "pid");
                    if (parts[1].equals (command)) return;  // exact match
                    // Otherwise, may be the wrapper script.
                    // The wrapper script is better than nothing, but keep scanning.
                }
            }
        }
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return;

        // Scan for PIDs chained from the given one. We need to kill them all.
        Set<Long> pids = new TreeSet<Long> ();
        pids.add (pid);
        try (AnyProcess proc = build ("ps", "-eo", "pid,ppid", "--no-header").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                String[] parts = line.trim ().split ("\\s+");
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
        command.add (force ? "-9" : "-15");
        for (long l : pids) command.add (String.valueOf (l));
        try (AnyProcess proc = build (command).start ();) {}
    }

    @Override
    public long getProcMem (long pid) throws Exception
    {
        try (AnyProcess proc = build ("ps", "-q", String.valueOf (pid), "-o", "pid,rss", "--no-header").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.trim ();
                String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
                long PID = Long.parseLong (parts[0]);
                long RSS = Long.parseLong (parts[1]);
                if (PID == pid) return RSS * 1024;
            }
        }
        return 0;
    }

    @Override
    public long getMemoryPhysicalTotal ()
    {
        // TODO
        return 0;
    }

    @Override
    public long getMemoryPhysicalFree ()
    {
        // TODO
        return 0;
    }

    @Override
    public int getProcessorTotal ()
    {
        // TODO
        return 0;
    }

    @Override
    public double getProcessorLoad ()
    {
        // TODO
        return 0;
    }
}
