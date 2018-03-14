/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.execenvs.Connection.Result;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class RemoteHost extends HostSystem
{
    // TODO: package remote file operations into a FileSystemProvider
    public void createDir (String path) throws Exception
    {
        Result r = Connection.exec ("mkdir -p '" + path + "'");
        if (r.error)
        {
            Backend.err.get ().println ("Could not create job directory: " + r.stdErr);
            throw new Backend.AbortRun ();
        }
    }

    @Override
    public void setFileContents (String path, String content) throws Exception
    {
        File tempFile = new File ("tempSetFileContents");  // Created in local working directory, which should be set to the job dir.
        stringToFile (tempFile, content);
        Result r = Connection.send (tempFile, path);
        if (r.error)
        {
            Backend.err.get ().println ("Could not send file content to remote system: " + r.stdErr);
            throw new Backend.AbortRun ();
        }
    }

    @Override
    public String getFileContents (String path) throws Exception
    {
        Result r = Connection.exec ("cat '" + path + "'");
        if (r.error)
        {
            Backend.err.get ().println (r.stdErr);
            throw new Backend.AbortRun ();
        }
        return r.stdOut;
    }

    @Override
    public void deleteJob (String jobName) throws Exception
    {
        String dir = getNamedValue ("directory.jobs");
        String path = dir + "/" + jobName;
        String rmCmd = "rm -rf '" + path + "'";
        Result r = Connection.exec (rmCmd);
        if (r.error)
        {
            Backend.err.get ().println (r.stdErr);
            throw new Backend.AbortRun ();
        }
    }

    @Override
    public void downloadFile (String path, File destPath) throws Exception
    {
        Result r = Connection.receive (path, destPath);
        if (r.error)
        {
            Backend.err.get ().println (r.stdErr);
            throw new Backend.AbortRun ();
        }
    }

    public long lastModified (String path)
    {
        try
        {
            Result r = Connection.exec ("ls --time-style=full '" + path + "'");
            if (r.error) return 0;
            String line = new BufferedReader (new StringReader (r.stdOut)).readLine ();
            if (line.contains ("No such")) return 0;
            String[] parts = line.split (" ");
            line = parts[5] + " " + parts[6];
            SimpleDateFormat f = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.S");
            Date d = f.parse (line);
            return d.getTime ();
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    public String file (String dirName, String fileName) throws Exception
    {
        return dirName + "/" + fileName;
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name"))           return "RedSky";
        if (name.equalsIgnoreCase ("xyce.binary"))    return "/ascldap/users/cewarr/srcXyce/Xyce/BUILD/XyceOpenMPI/src/Xyce";
        if (name.equalsIgnoreCase ("directory.jobs"))
        {
            try
            {
                Result r = Connection.exec ("cd; pwd");  // return to home directory and print it; probably pwd alone is sufficient
                String line = new BufferedReader (new StringReader (r.stdOut)).readLine ();
                return line + "/.n2a";
            }
            catch (Exception e)
            {
                return defaultValue;
            }
        }
        if (name.equalsIgnoreCase ("c.directory"))
        {
            try
            {
                Result r = Connection.exec ("cd; pwd");  // return to home directory and print it; probably pwd alone is sufficient
                String line = new BufferedReader (new StringReader (r.stdOut)).readLine ();
                return line + "/.n2a_cruntime";
            }
            catch (Exception e)
            {
                return defaultValue;
            }
        }
        return super.getNamedValue (name, defaultValue);
    }
}
