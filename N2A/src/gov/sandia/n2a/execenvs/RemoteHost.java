/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.Connection.Result;
import gov.sandia.n2a.plugins.extpoints.Backend;

/**
    Wraps access to any system other than localhost.
    This default implementation is suitable for a unix-like system that runs jobs on its
    own processors, as opposed to queuing them on a cluster or specialized hardware.
**/
public class RemoteHost extends HostSystem
{
    FileSystem sshfs;

    @Override
    public boolean isActive (MNode job) throws Exception
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid == 0) return false;

        Result r = Connection.exec ("ps -o pid,command " + String.valueOf (pid));
        if (r.error) return false;
        try (BufferedReader reader = new BufferedReader (new StringReader (r.stdOut)))
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

        Result r = Connection.exec ("ps ux");
        if (r.error)
        {
            Backend.err.get ().println (r.stdErr);
            throw new Backend.AbortRun ();
        }
        try (BufferedReader reader = new BufferedReader (new StringReader (r.stdOut)))
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
        Connection.exec (command + " > '" + prefix + "/out' 2>> '" + prefix + "/err' &", true);

        // Get PID of newly-created job
        Result r = Connection.exec ("ps -ewwo pid,command");
        if (r.error) return;
        try (BufferedReader reader = new BufferedReader (new StringReader (r.stdOut)))
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
    public void killJob (long pid, boolean force) throws Exception
    {
        Connection.exec ("kill -" + (force ? 9 : 15) + " " + pid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Path getResourceDir () throws Exception
    {
        // TODO: also check if ssh connection is still live.
        if (sshfs == null)
        {
            String hostname = metadata.getOrDefault (name, "hostname");
            String username = metadata.getOrDefault (System.getProperty ("user.name"), "username");
            sshfs = FileSystems.newFileSystem (new URI ("ssh.unix://" + hostname + "/home/" + username), Collections.EMPTY_MAP);
        }
        return sshfs.getPath ("n2a");
    }

    @Override
    public long getProcMem (long pid) throws Exception
    {
        Result r = Connection.exec ("ps -q " + String.valueOf (pid) + " -o pid,rss --no-header");
        if (r.error) return 0;
        try (BufferedReader reader = new BufferedReader (new StringReader (r.stdOut)))
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
