/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import gov.sandia.n2a.db.MNode;

/**
    Wraps access to any system other than localhost.
    This default implementation is suitable for a unix-like system that runs jobs on its
    own processors, as opposed to queuing them on a cluster or specialized hardware.
**/
public class RemoteUnix extends Host
{
    //protected Connection connection;
    public Connection connection;  // public for testing

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "RemoteUnix";
            }

            public Host createInstance ()
            {
                return new RemoteUnix ();
            }
        };
    }

    public void connect () throws Exception
    {
        if (connection == null) connection = new Connection (config);
        connection.connect ();
    }

    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return false;

        try (AnyProcess proc = build ("ps -o pid,command " + String.valueOf (pid)).start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.trim ();
                String[] parts = line.split ("\\s+", 2);  // any amount/type of whitespace forms the delimiter
                long pidListed = Long.parseLong (parts[0]);
                // TODO: save remote command in job record, then compare it here. PID alone is not enough to be sure job is running.
                if (pid == pidListed) return true;
            }
        }
        return false;
    }

    @Override
    public Set<Long> getActiveProcs () throws Exception
    {
        Set<Long> result = new TreeSet<Long> ();

        try (AnyProcess proc = build ("ps ux").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains ("ps ux")) continue;
                if (! line.contains ("model")) continue;
                line = line.trim ();
                String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
                result.add (new Long (parts[1]));  // pid is second column
            }
        }
        return result;
    }

    @Override
    public void submitJob (MNode job, String command) throws Exception
    {
        String prefix = command.substring (0, command.lastIndexOf ("/"));
        try (AnyProcess proc = build (command + " > '" + prefix + "/out' 2>> '" + prefix + "/err' &").start ())
        {
            proc.wait ();
        }

        // Get PID of newly-created job
        try (AnyProcess proc = build ("ps -ewwo pid,command").start ();
             BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                line = line.trim ();
                String[] parts = line.split ("\\s+");  // any amount/type of whitespace forms the delimiter
                job.set (Long.parseLong (parts[0]), "$metadata", "pid");
                return;
            }
        }
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return;

        try (AnyProcess proc = build ("kill -" + (force ? 9 : 15) + " " + pid).start ())
        {
            proc.wait ();  // To avoid killing the process by closing the channel.
        }
    }

    @Override
    public AnyProcessBuilder build (String... command) throws Exception
    {
        connect ();
        return connection.build (command);
    }

    @Override
    public Path getResourceDir () throws Exception
    {
        connect ();
        return connection.getFileSystem ().getPath ("n2a");  // assumes that filesystem default directory is where n2a dir should reside
    }

    @Override
    public String quotePath (Path path)
    {
        String result = path.toString ();
        if (result.contains (" ")) return "\'" + result + "\'";
        return result;
    }

    @Override
    public long getProcMem (long pid) throws Exception
    {
        try (AnyProcess proc = build ("ps -q " + String.valueOf (pid) + " -o pid,rss --no-header").start ();
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
