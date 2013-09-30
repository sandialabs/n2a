/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.execenvs;

import gov.sandia.umf.platform.execenvs.beans.AllJobInfo;
import gov.sandia.umf.platform.execenvs.beans.DateGroup;
import gov.sandia.umf.platform.execenvs.beans.Job;
import gov.sandia.umf.platform.execenvs.beans.Resource;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import replete.util.FileUtil;
import replete.util.User;


public abstract class LocalMachineEnv extends ExecutionEnv
{
    @Override
    public AllJobInfo getJobs () throws Exception
    {
        AllJobInfo result = new AllJobInfo ();

        File dir = new File (getNamedValue ("directory.jobs"));
        File[] jobDirs = dir.listFiles ();
        if (jobDirs == null)
        {
            return result;
        }
        for (File jobDir : jobDirs)
        {
            if (! jobDir.isDirectory ())
            {
                continue;
            }

            String jobName = jobDir.getName ();
            DateGroup group = result.addDateGroup (jobName.substring (0, 10));
            Job job = group.addJob (jobName);

            File[] jobFiles = jobDir.listFiles ();
            for (File jobFile : jobFiles)
            {
                job.addResource (new Resource (jobFile.getAbsolutePath ()));
            }
        }

        return result;
    }

    @Override
    public String createJobDir () throws Exception
    {
        File dir = new File (getNamedValue ("directory.jobs"));
        String jobName = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
        File jobDir = new File (dir, jobName);
        if (! jobDir.mkdirs ())
        {
            throw new Exception ("Could not create job directory");
        }
        return jobDir.getAbsolutePath ();
    }

    @Override
    public void createDir (String path) throws Exception
    {
        File dir = new File (path);
        if (dir.isDirectory ())
        {
            return;
        }
        if (! dir.mkdirs ())
        {
            throw new Exception ("Could not create directory: " + path);
        }
    }

    @Override
    public String build (String sourceFile, String runtime) throws Exception
    {
        String binary = sourceFile + ".bin";
        String dir = new File (runtime).getParent ();

        // Need handle cl, and maybe others as well.
        String compiler = getNamedValue ("c.compiler", "g++");

        String [] commands = {compiler, "-O3", "-o", binary, "-I" + dir, runtime, "-x", "c++", sourceFile};
        Process p = Runtime.getRuntime ().exec (commands);
        p.waitFor ();
        if (p.exitValue () != 0) throw new Exception ("Failed to compile:\n" + FileUtil.getTextContent (p.getErrorStream ()));

        return binary;
    }

    @Override
    public String buildRuntime (String sourceFile) throws Exception
    {
        File f = new File (sourceFile);
        String stem = f.getName ();
        int index = stem.lastIndexOf (".");
        if (index > 0)
        {
            stem = stem.substring (0, index);
        }
        String dir = f.getParent ();
        String binary = new File (dir, stem + ".o").getAbsolutePath ();

        long sourceDate = lastModified (sourceFile);
        if (sourceDate == 0) throw new Exception ("No source file for runtime");
        if (lastModified (binary) > sourceDate) return binary;  // early out, because we are already compiled

        String compiler = getNamedValue ("c.compiler", "g++");

        String [] commands = {compiler, "-c", "-O3", "-I" + dir, "-o", binary, "-x", "c++", sourceFile};
        Process p = Runtime.getRuntime ().exec (commands);
        p.waitFor ();
        if (p.exitValue () != 0) throw new Exception ("Failed to compile:\n" + FileUtil.getTextContent (p.getErrorStream ()));

        return binary;
    }

    @Override
    public void setFileContents (String path, String content) throws Exception
    {
        FileUtil.writeTextContent (new File (path), content);
    }

    @Override
    public String getFileContents (String path) throws Exception
    {
        return FileUtil.getTextContent (new File (path));
    }

    @Override
    public void deleteJob (String jobName) throws Exception
    {
        File dir = new File (getNamedValue ("directory.jobs"));
        File jobDir = new File (dir, jobName);
        File[] jobFiles = jobDir.listFiles ();
        for (File jobFile : jobFiles)
        {
            jobFile.delete ();
        }
        jobDir.delete ();
    }

    @Override
    public void downloadFile (String path, File destPath) throws Exception
    {
        File file = new File(path);
        FileUtil.copy (file, destPath);
    }

    @Override
    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name"))
        {
            return "Local";
        }
        if (name.equalsIgnoreCase ("directory.jobs"))  // name "env.directory.job" if this is moved to global scope
        {
            try
            {
                File n2a = new File (User.getHome (), ".n2a");
                return new File (n2a, "jobs").getAbsolutePath ();
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
                File n2a = new File (User.getHome (), ".n2a");
                return new File (n2a, "cruntime").getAbsolutePath ();
            }
            catch (Exception e)
            {
                return defaultValue;
            }
        }
        if (name.equalsIgnoreCase ("c.compiler"))
        {
            return "g++";
        }
        if (name.equalsIgnoreCase ("xyce.binary"))
        {
            return "xyce";
        }
        return super.getNamedValue (name, defaultValue);
    }
}
