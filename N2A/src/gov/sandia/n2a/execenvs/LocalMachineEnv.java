/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.execenvs.beans.AllJobInfo;
import gov.sandia.n2a.execenvs.beans.DateGroup;
import gov.sandia.n2a.execenvs.beans.Job;
import gov.sandia.n2a.execenvs.beans.Resource;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
            Backend.err.get ().println ("Could not create job directory");
            throw new Backend.AbortRun ();
        }
        return jobDir.getAbsolutePath ();
    }

    @Override
    public Path build (Path source, Path runtime) throws Exception
    {
        String stem = source.getFileName ().toString ().split ("\\.", 2)[0];
        Path binary = source.getParent ().resolve (stem + ".bin");
        Path dir = runtime.getParent ();

        // Need to handle cl, and maybe others as well.
        String compiler = getNamedValue ("c.compiler", "g++");

        String [] commands = {compiler, "-O3", "-o", binary.toString (), "-I" + dir, runtime.toString (), "-std=c++11", source.toString ()};
        Process p = Runtime.getRuntime ().exec (commands);
        p.waitFor ();
        if (p.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to compile:\n" + streamToString (p.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        return binary;
    }

    @Override
    public Path buildRuntime (Path sourceFile) throws Exception
    {
        String stem = sourceFile.getFileName ().toString ();
        int index = stem.lastIndexOf (".");
        if (index > 0)
        {
            stem = stem.substring (0, index);
        }
        Path dir = sourceFile.getParent ();
        Path binary = dir.resolve (stem + ".o");

        long sourceDate = lastModified (sourceFile);
        if (sourceDate == 0)
        {
            Backend.err.get ().println ("No source file for runtime");
            throw new Backend.AbortRun ();
        }
        if (lastModified (binary) > sourceDate) return binary;  // early out, because we are already compiled

        String compiler = getNamedValue ("c.compiler", "g++");

        String [] commands = {compiler, "-c", "-O3", "-I" + dir, "-o", binary.toString (), "-std=c++11", sourceFile.toString ()};
        Process p = Runtime.getRuntime ().exec (commands);
        p.waitFor ();
        if (p.exitValue () != 0)
        {
            Backend.err.get ().println ("Failed to compile:\n" + streamToString (p.getErrorStream ()));
            throw new Backend.AbortRun ();
        }

        return binary;
    }

    @Override
    public void setFileContents (String path, String content) throws Exception
    {
        stringToFile (new File (path), content);
    }

    @Override
    public String getFileContents (String path) throws Exception
    {
        return fileToString (new File (path));
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
        Files.copy (Paths.get (path), destPath.toPath (), StandardCopyOption.REPLACE_EXISTING);
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
            return AppData.runs.get ();
        }
        if (name.equalsIgnoreCase ("c.directory"))
        {
            return new File (AppData.properties.get ("resourceDir"), "cruntime").getAbsolutePath ();
        }
        if (name.equalsIgnoreCase ("c.compiler"))
        {
            return "g++";
        }
        if (name.equalsIgnoreCase ("xyce.binary"))
        {
            return "xyce";
        }
        if (name.equalsIgnoreCase ("stpu.directory"))
        {
            return new File (AppData.properties.get ("resourceDir"), "STPUruntime").getAbsolutePath ();
        }
        return super.getNamedValue (name, defaultValue);
    }
}
