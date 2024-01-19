/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MCheckBox;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

public class Windows extends Host
{
    protected EditorPanel panel;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "Windows";
            }

            public Host createInstance ()
            {
                return new Windows ();
            }
        };
    }

    @Override
    public JPanel getEditor ()
    {
        if (panel == null) panel = new EditorPanel ();
        return panel;
    }

    @SuppressWarnings("serial")
    public class EditorPanel extends JPanel
    {
        public MCheckBox fieldUseActiveProcs = new MCheckBox (config, "useActiveProcs", "Use existing jobs to estimate cost of adding another job", true);

        public EditorPanel ()
        {
            Lay.BLtg (this, "N",
                Lay.BxL ("V",
                    Lay.FL (fieldUseActiveProcs)
                )
            );
        }
    }

    @Override
    public boolean isAlive (MNode job) throws Exception
    {
        String jobDir = Paths.get (job.get ()).getParent ().toString ();
        Process proc = new ProcessBuilder ("powershell", "get-ciminstance", "win32_process", "|", "select", "commandline", "|", "convertto-csv").start ();
        proc.getOutputStream ().close ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (line.contains (jobDir)) return true;
            }
        }
        return false;
    }

    @Override
    public List<ProcessInfo> getActiveProcs () throws Exception
    {
        List<ProcessInfo> result = new ArrayList<ProcessInfo> ();
        Map<Long,  ProcessInfo> id2process   = new HashMap<Long,  ProcessInfo> ();
        Map<String,ProcessInfo> name2process = new HashMap<String,ProcessInfo> ();

        Path   resourceDir = getResourceDir ();
        String jobsDir     = resourceDir.resolve ("jobs").toString ();

        Process proc = new ProcessBuilder ("powershell", "get-ciminstance", "win32_process", "|", "select", "processid,workingsetsize,commandline", "|", "convertto-csv").start ();
        proc.getOutputStream ().close ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (! line.contains (jobsDir)) continue;

                ProcessInfo info = new ProcessInfo ();

                String[] parts = line.split (",", 2);
                info.pid = Long.valueOf (parts[0].replace ("\"", ""));

                parts = parts[1].split (",", 2);
                info.memory = Long.valueOf (parts[0].replace ("\"", ""));

                int pos = parts[1].indexOf (jobsDir);
                if (pos >= 0)
                {
                    String temp = parts[1].substring (pos + jobsDir.length () + 1);  // +1 because jobsDir does not end with slash
                    pos = temp.indexOf ("\\");
                    if (pos >= 0) info.jobKey = temp.substring (0, pos);
                    else          info.jobKey = temp;
                }

                result.add (info);
                id2process.put (info.pid, info);
            }
        }

        // Extra awkward work to get cpu utilization out of powershell
        proc = new ProcessBuilder ("powershell", "get-counter", "\"\"\"\\process(*)\\id process\"\"\",\"\"\"\\process(*)\\% processor time\"\"\"").start ();  // Yes, we actually have to pass three quotes per each actual quote here. There may be better ways, but this works.
        proc.getOutputStream ().close ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                String[] pieces = line.split ("process\\(", 2);
                if (pieces.length == 1) continue;

                pieces = pieces[1].split ("\\)", 2);
                String name = pieces[0];

                String line2 = reader.readLine ().trim ();

                if (line.contains ("id process"))
                {
                    long value = Long.valueOf (line2);
                    ProcessInfo info = id2process.get (value);
                    if (info != null)
                    {
                        name2process.put (name, info);
                    }
                }
                else if (line.contains ("processor time"))
                {
                    ProcessInfo info = name2process.get (name);
                    if (info != null) info.cpu = Double.valueOf (line2) / 100;
                }
            }
        }

        // Consolidate multiple processes associated with the same job.
        for (int i = result.size () - 1; i >= 0; i--)
        {
            ProcessInfo I = result.get (i);
            for (int j = i - 1; j >= 0; j--)
            {
                ProcessInfo J = result.get (j);
                if (I.jobKey != null  &&  J.jobKey != null  &&  I.jobKey.equals (J.jobKey))
                {
                    J.cpu    += I.cpu;
                    J.memory += I.memory;
                    result.remove (i);
                    break;
                }
            }
        }

        return result;
    }

    @Override
    public void submitJob (MNode job, boolean out2err, List<List<String>> commands, List<Path> libPath) throws Exception
    {
        Path jobDir = Paths.get (job.get ()).getParent ();
        Path script = jobDir.resolve ("n2a_job.bat");
        String out = out2err ? "err1" : "out";  // Windows cmd is too stupid to append two streams to the same file, so we have to use something different than "err" here.
        try (BufferedWriter writer = Files.newBufferedWriter (script))
        {
            writer.append ("cd " + jobDir + "\r\n");

            if (libPath != null  &&  ! libPath.isEmpty ())
            {
                StringBuilder pathString = new StringBuilder ();
                pathString.append ("set PATH=");
                for (Path p : libPath) pathString.append (p + ";");  // Quoting is not necessary.
                pathString.append ("%PATH%");
                writer.append (pathString + "\n");
                writer.write ("\n");
            }

            String combined = combine (commands.get (0));
            writer.append (combined + " >> " + out + " 2>> err\n");

            // Checking exit codes in a Windows batch file is somewhat futile,
            // because sometimes the batch terminates immediately after the first
            // substantial command is called, even if it apparently succeeds.
            // Often the same batch will run to completion if called manually
            // from a command shell. To make this even more confusing, the same
            // program (say from the C backend) will sometimes interrupt the batch
            // and sometimes not. A deliberately-induced segfault actually returns
            // an exit code, so this isn't simply about the program crashing.
            // NodeJob.monitorProgress() has some ability to handle this situation.
            // If the job output indicates a complete run, it will be treated as
            // successful.
            // TODO: Solve this mystery and get more reliable job monitoring on Windows.
            int count = commands.size ();
            for (int i = 1; i < count; i++)
            {
                combined = combine (commands.get (i));
                writer.append ("if errorlevel 0 (" + combined + " >> " + out + " 2>> err)\n");
            }

            writer.append ("if errorlevel 0 (\r\n");
            writer.append ("  echo success > finished\r\n");
            writer.append (") else (\r\n");
            writer.append ("  echo failure > finished\r\n");
            writer.append (")\r\n");
        }

        // Start batch in a detached process. This very awkward to do from within Java.
        // "start /b ..." will start an independent shell to run the batch.
        // However, "start" is an internal command, so it can't be called directly.
        // Instead, we need "cmd /c ..." to run start.
        Process proc = new ProcessBuilder ("cmd", "/c", "start", "/b", script.toString ()).start ();
        proc.waitFor ();
        if (proc.exitValue () != 0)
        {
            String stdErr = streamToString (proc.getErrorStream ());
            throw new Backend.AbortRun ("Failed to run job:\n" + stdErr);
        }

        // Wait until process starts.
        String jobDirString = jobDir.toString ();
        long start = System.currentTimeMillis ();
        while (System.currentTimeMillis () - start < 3000)  // Wait up to 3 seconds.
        {
            proc = new ProcessBuilder ("powershell", "get-ciminstance", "win32_process", "|", "select", "commandline", "|", "convertto-csv").start ();
            proc.getOutputStream ().close ();
            try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
            {
                String line;
                while ((line = reader.readLine ()) != null)
                {
                    if (line.contains (jobDirString)) return;
                }
            }
        }
    }

    @Override
    public void killJob (MNode job, boolean force) throws Exception
    {
        // Windows does not provide a simple way to signal a non-GUI process.
        // Instead, the program is responsible to poll for the existence of the "finished" file
        // on a reasonable interval, say once per second. See Backend.kill()
        if (! force) return;

        Path jobDir = Paths.get (job.get ()).getParent ();
        String jobDirString = jobDir.toString ();
        Process proc = new ProcessBuilder ("powershell", "get-ciminstance", "win32_process", "|", "select", "processid,commandline", "|", "convertto-csv").start ();
        proc.getOutputStream ().close ();
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
        {
            String line;
            while ((line = reader.readLine ()) != null)
            {
                if (! line.contains (jobDirString)) continue;
                String[] parts = line.split (",", 2);
                String pid = parts[0].replace ("\"", "");
                new ProcessBuilder ("taskkill", "/PID", pid, "/F").start ();
            }
        }
    }

    @Override
    public String shellSuffix ()
    {
        return ".bat";
    }

    public void deleteTree (Path start)
    {
        // Hack to deal with file locks held by NIO.
        // See notes on Host.DeleteTreeVisitor ctor.
        System.gc ();

        if (Files.isDirectory (start))
        {
            try (AnyProcess proc = build ("cmd", "/c", "rd", "/q", "/s", quote (start)).start ()) {}
            catch (Exception e) {}
        }
        else  // single file
        {
            try (AnyProcess proc = build ("cmd", "/c", "del", "/q", "/f", quote (start)).start ()) {}
            catch (Exception e) {}
        }
    }
}
