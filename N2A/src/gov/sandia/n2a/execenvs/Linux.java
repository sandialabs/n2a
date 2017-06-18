/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

public class Linux extends LocalMachineEnv
{
    @Override
    public Set<Long> getActiveProcs () throws Exception
    {
        Set<Long> result = new TreeSet<Long> ();
        String[] cmdArray = new String[] {"ps", "-eo", "pid,command"};
        Process proc = Runtime.getRuntime ().exec (cmdArray);
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
    public long submitJob (MNode job, String command) throws Exception
    {
        String jobDir = new File (job.get ()).getParent ();

        File out = new File (jobDir, "out");
        File err = new File (jobDir, "err");

        File script = new File (jobDir, "n2a_job");
        stringToFile (script, "#!/bin/bash\n" + command + " > '" + out.getAbsolutePath () + "' 2>> '" + err.getAbsolutePath () + "' &\ntouch finished\n");

        String [] commandParm = {"chmod", "u+x", script.getAbsolutePath ()};
        Process p = Runtime.getRuntime ().exec (commandParm);
        p.waitFor ();
        if (p.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to change permissions on job script:\n" + streamToString (p.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        commandParm = new String[] {script.getAbsolutePath ()};
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
                    return Long.parseLong (parts[0]);
                }
            }
        }
        return 0;
    }

    @Override
    public void killJob (long pid) throws Exception
    {
        Runtime.getRuntime ().exec (new String[] {"kill", "-9", String.valueOf (pid)});
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
