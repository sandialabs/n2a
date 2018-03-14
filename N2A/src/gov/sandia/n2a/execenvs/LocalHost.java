/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.AppData;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public abstract class LocalHost extends HostSystem
{
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
