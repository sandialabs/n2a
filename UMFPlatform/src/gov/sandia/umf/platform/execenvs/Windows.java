/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.execenvs;

import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.RunState;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import replete.process.ProcessUtil;
import replete.util.FileUtil;

public class Windows extends LocalMachineEnv
{
    @Override
    public Set<Integer> getActiveProcs () throws Exception
    {
        Set<Integer> result = new TreeSet<Integer> ();
        String[] cmdArray = new String[] {"tasklist", "/v", "/fo", "table"};
        String[] lines = ProcessUtil.getOutput (cmdArray);
        for (String line : lines)
        {
            line = line.toLowerCase ();
            if (line.contains ("n2a_job"))
            {
                // If need to use /b for background and don't get title info anymore
                String[] parts = line.split ("\\s+");
                result.add (new Integer (parts[1]));
                System.out.println ("FOUND! " + line);
            }
        }
        return result;
    }

    @Override
    public void submitJob (RunState run) throws Exception
    {
        String command = run.getNamedValue ("command");
        String jobDir  = run.getNamedValue ("jobDir");
        File out = new File (jobDir, "out");
        File err = new File (jobDir, "err");

        File script = new File (jobDir, "n2a_job.bat");
        FileUtil.writeTextContent (script, command + " > " + quotePath (out.getAbsolutePath ()) + " 2> " + quotePath (err.getAbsolutePath ()) + "\r\n");
        String [] commandParm = new String[] {"cmd", "/c", "start", "/b", script.getAbsolutePath ()};
        Process p = Runtime.getRuntime ().exec (commandParm);
        p.waitFor ();
        if (p.exitValue () != 0) throw new Exception ("Failed to run job:\n" + FileUtil.getTextContent (p.getErrorStream ()));
    }

    @Override
    public String quotePath (String path)
    {
        return "\"" + path + "\"";
    }

    @Override
    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name"))
        {
            return "Windows";
        }
        return super.getNamedValue (name, defaultValue);
    }

	@Override
	public long getProcMem(Integer procNum) {
		// TODO Auto-generated method stub
		return 0;
	}
}
