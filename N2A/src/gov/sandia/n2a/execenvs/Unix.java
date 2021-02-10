/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

            public boolean isRemote ()
            {
                return false;
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
    public List<ProcessInfo> getActiveProcs () throws Exception
    {
        List<ProcessInfo> result = new ArrayList<ProcessInfo> ();

        Path   resourceDir = getResourceDir ();
        String jobsDir     = resourceDir.resolve ("jobs").toAbsolutePath ().toString ();

        try (AnyProcess proc = build ("ps", "-ewwo", "pid,pcpu,rss,command", "--no-header").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
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
                    info.cpu = Double.valueOf (parts[0]);

                    parts = parts[1].trim ().split (" ", 2);
                    info.memory = Long.valueOf (parts[0]);

                    result.add (info);
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
    public long getMemoryTotal ()
    {
        try (AnyProcess proc = build ("cat", "/proc/meminfo").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (! line.startsWith ("MemTotal:")) continue;
                String[] parts = line.trim ().split ("\\s+");
                return Long.parseLong (parts[1]) * 1024;  // assumes "kB" is unit
            }
        }
        catch (Exception e) {}
        return 0;
    }

    @Override
    public long getMemoryFree ()
    {
        try (AnyProcess proc = build ("cat", "/proc/meminfo").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (! line.startsWith ("MemAvailable:")) continue;
                String[] parts = line.trim ().split ("\\s+");
                return Long.parseLong (parts[1]) * 1024;
            }
        }
        catch (Exception e) {}
        return 0;
    }

    @Override
    public int getProcessorTotal ()
    {
        try (AnyProcess proc = build ("nproc").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line = reader.readLine ();
            return Integer.parseInt (line);
        }
        catch (Exception e) {}
        return 1;
    }

    @Override
    public double getProcessorIdle ()
    {
        // Approach: Read /proc/stat twice and use differences in jiffy count to determine idle time.
        long deltaTotal = 1;
        long deltaIdle  = 1;
        try (AnyProcess proc = build ("head", "-1", "/proc/stat").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line = reader.readLine ();
            String[] pieces = line.split ("\\s+");
            long user   = Long.parseLong (pieces[1]);
            long system = Long.parseLong (pieces[3]);
            long idle   = Long.parseLong (pieces[4]);
            deltaTotal = -user - system - idle;
            deltaIdle  = -idle;
        }
        catch (Exception e) {}

        // Fetch number of processors, then fill out remaining time to get 1 second delay between readings.
        long startTime = System.currentTimeMillis ();
        int nproc = getProcessorTotal ();
        long duration = System.currentTimeMillis () - startTime;
        long wait = 1000 - duration;  // target is 1 second
        if (wait > 0)
        {
            try {Thread.sleep (wait);}
            catch (InterruptedException e) {}
        }

        try (AnyProcess proc = build ("head", "-1", "/proc/stat").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line = reader.readLine ();
            String[] pieces = line.split ("\\s+");
            long user   = Long.parseLong (pieces[1]);
            long system = Long.parseLong (pieces[3]);
            long idle   = Long.parseLong (pieces[4]);
            deltaTotal += user + system + idle;
            deltaIdle  += idle;
        }
        catch (Exception e) {}

        double idle = (double) deltaIdle / deltaTotal;
        return nproc * idle;
    }
}
