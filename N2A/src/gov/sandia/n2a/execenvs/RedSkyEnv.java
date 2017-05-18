/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.execenvs.beans.AllJobInfo;
import gov.sandia.n2a.execenvs.beans.DateGroup;
import gov.sandia.n2a.execenvs.beans.Job;
import gov.sandia.n2a.execenvs.beans.Resource;
import gov.sandia.n2a.ssh.RedSkyConnection;
import gov.sandia.n2a.ssh.RedSkyConnection.Result;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public abstract class RedSkyEnv extends ExecutionEnv
{
    @Override
    public AllJobInfo getJobs () throws Exception
    {
        String dir = getNamedValue ("directory.jobs");
        int dirLength = dir.length ();
        Result r = RedSkyConnection.exec ("find '" + dir + "'");
        if (r.error) throw new Exception (r.stdErr);

        AllJobInfo result = new AllJobInfo ();
        BufferedReader reader = new BufferedReader (new StringReader (r.stdOut));
        String line;
        while ((line = reader.readLine ()) != null)
        {
            int idx     = line.indexOf ("/", dirLength);
            if (idx     == -1) continue;
            int nextIdx = line.indexOf ("/", idx + 1);
            if (nextIdx == -1) continue;

            String jobName = line.substring (idx + 1, nextIdx);
            DateGroup group = result.addDateGroup (jobName.substring (0, 10));
            Job job = group.addJob (jobName);
            job.addResource (new Resource (line));
        }

        return result;
    }

    @Override
    public String createJobDir () throws Exception
    {
        String dir = getNamedValue ("directory.jobs");
        String jobName = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
        String jobDir = dir + "/" + jobName;
        Result r = RedSkyConnection.exec ("mkdir -p '" + jobDir + "'");
        if (r.error) throw new Exception ("Could not create job directory: " + r.stdErr);
        return jobDir;
    }

    @Override
    public void createDir (String path) throws Exception
    {
        Result r = RedSkyConnection.exec ("mkdir -p '" + path + "'");
        if (r.error) throw new Exception ("Could not create job directory: " + r.stdErr);
    }

    @Override
    public String build (String sourceFile, String runtime) throws Exception
    {
        String binary = sourceFile + ".bin";
        Result r = RedSkyConnection.exec ("g++ -o '" + binary + "' '" + runtime + "' -x c++ '" + sourceFile + "'");
        if (r.error) throw new Exception ("Failed to compile: " + r.stdErr);
        return binary;
    }

    @Override
    public String buildRuntime (String sourceFile) throws Exception
    {
        throw new Exception ("Not implemented");
    }

    @Override
    public void setFileContents (String path, String content) throws Exception
    {
        File tempFile = new File ("tempSetFileContents");  // Created in local working directory, which should be set to the job dir.
        stringToFile (tempFile, content);
        Result r = RedSkyConnection.send (tempFile, path);
        if (r.error) throw new Exception ("Could not send file content to remote system: " + r.stdErr);
    }

    @Override
    public String getFileContents (String path) throws Exception
    {
        Result r = RedSkyConnection.exec ("cat '" + path + "'");
        if (r.error) throw new Exception (r.stdErr);
        return r.stdOut;
    }

    @Override
    public void deleteJob (String jobName) throws Exception
    {
        String dir = getNamedValue ("directory.jobs");
        String path = dir + "/" + jobName;
        String rmCmd = "rm -rf '" + path + "'";
        Result r = RedSkyConnection.exec (rmCmd);
        if (r.error) throw new Exception (r.stdErr);
    }

    @Override
    public void downloadFile (String path, File destPath) throws Exception
    {
        Result r = RedSkyConnection.receive (path, destPath);
        if (r.error) throw new Exception(r.stdErr);
    }

    public long lastModified (String path)
    {
        try
        {
            Result r = RedSkyConnection.exec ("ls --time-style=full '" + path + "'");
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
                Result r = RedSkyConnection.exec ("cd; pwd");  // return to home directory and print it; probably pwd alone is sufficient
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
                Result r = RedSkyConnection.exec ("cd; pwd");  // return to home directory and print it; probably pwd alone is sufficient
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
