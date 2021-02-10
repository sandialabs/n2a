/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.Host;
import gov.sandia.n2a.execenvs.Host.ProcessInfo;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;


public abstract class Backend implements ExtensionPoint
{
    /**
        A stream bound to the "err" file in the directory of the job currently
        being prepared, primarily for use by execute(MNode). Any warnings or
        errors should be written here. The simulator should be configured to
        append its stderr to the same file, if possible. This implies the print
        stream should be closed before launching the external command.
    **/
    public static ThreadLocal<PrintStream> err = new ThreadLocal<PrintStream> ()
    {
        @Override
        protected PrintStream initialValue ()
        {
            return System.err;
        }
    };

    /**
        This exception indicates to stop the simulation, but don't add any more
        text to the err PrintStream. Implicitly, the thrower has already printed
        out a sufficient message, and a java stack trace would simply add clutter.
    **/
    @SuppressWarnings("serial")
    public static class AbortRun extends RuntimeException
    {
    }

    /**
        Finds the Backend instance that matches the given name.
        If no match is found, returns the Internal backend.
        If Internal is missing, the system is too broken to run.
    **/
    public static Backend getBackend (String name)
    {
        Backend result = null;
        Backend internal = null;
        for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Backend.class))
        {
            Backend s = (Backend) ext;
            if (s.getName ().equalsIgnoreCase (name))
            {
                result = s;
                break;
            }
            if (s.getName ().equals ("Internal")) internal = s;
        }
        if (result == null) result = internal;
        if (result == null) throw new RuntimeException ("Couldn't find internal simulator!");
        return result;
    }
 
    /**
        Returns the display name for this back-end.
    **/
    public abstract String getName ();

    /**
        Indicates that resources are available to execute the job.
        This requires knowledge of three things:
        * The specifics of the job (how big the model will be).
        * The nature of the specific backend, such as how much resources it needs to handle the given model size.
        * The target machine (available memory, CPUs, etc.) Information needed to determine this should be embedded in the job metadata.
    **/
    public boolean canRunNow (Host host, MNode model)
    {
        // Limited resources include:
        // * Physical processors
        // * Physical memory
        // * Disk space
        // It is difficult to estimate how much of these resources a given job
        // will consume, so we make a very crude guess.
        try
        {
            double cpuNeeded    = 0;
            long   memoryNeeded = 0;
            List<ProcessInfo> procs = host.getActiveProcs ();
            for (ProcessInfo info : procs)
            {
                cpuNeeded    += info.cpu;
                memoryNeeded += info.memory;
            }

            int processCount = procs.size ();
            if (processCount == 0)
            {
                cpuNeeded    = 1;         // Must have at least 1 core available to launch process.
                memoryNeeded = 0x1 << 30; // Must have at least 1GiB available.
            }
            else
            {
                // Assume that all processes are running the same model, so average makes sense.
                // Could also take max.
                cpuNeeded    /= processCount;
                memoryNeeded /= processCount;
            }

            if (host.getProcessorIdle () < cpuNeeded) return false;
            if (host.getMemoryFree () < memoryNeeded) return false;
        }
        catch (Exception e)
        {
            return false;
        }

        return true;
    }

    /**
        For a local machine, start the process that actually computes the job.
        For a remote system, submit the job to whatever scheduler may exist there.
    **/
    public void start (MNode job)
    {
    }

    /**
        Stop the the run.
        @param force If false, signal the job to shut down gracefully.
        If true, terminate the process. For a local machine, this could involve killing the process.
        For a remote machine, this could involve asking the scheduler to kill the job.
    **/
    public void kill (MNode job, boolean force)
    {
        // This default implementation is suitable for most backends, even if the job is running remotely.
        try
        {
            Host.get (job).killJob (job, force);
            Path localJobDir = Paths.get (job.get ()).getParent ();
            Files.copy (new ByteArrayInputStream ("killed" .getBytes ("UTF-8")), localJobDir.resolve ("finished"));
            // Note that BackendC on Windows uses the mere existence of the "finished" file as a signal to shut down gracefully.
            // This is due to the lack of a proper SIGTERM in Windows.
        }
        catch (Exception e) {}
    }

    /**
        Return an estimate of the current $t in the active simulation.
    **/
    public double currentSimTime (MNode job)
    {
        return getSimTimeFromOutput (job, "out", 0);
    }

    public static double getSimTimeFromOutput (MNode job, String outFileName, int timeColumn)
    {
        Path out;
        try
        {
            Host env         = Host.get (job);
            Path resourceDir = env.getResourceDir ();
            Path jobDir      = Host.getJobDir (resourceDir, job);
            out              = jobDir.resolve (outFileName);
        }
        catch (Exception e)
        {
            return 0;
        }

        // The divide by 2 at the end of the following line allows us to adapt down as well as up.
        int lineLength = job.getOrDefault (32, "$metadata", "backend", "all", "lineLength") / 2;

        try (SeekableByteChannel channel = Files.newByteChannel (out, StandardOpenOption.READ))
        {
            int columnIndex = 0;
            while (lineLength <= 1024*1024)  // limit to 1MiB
            {
                long available = channel.size ();
                if (available < lineLength) break;  // Don't exceed file size

                channel.position (available - lineLength);
                ByteBuffer buffer = ByteBuffer.allocate (lineLength);  // TODO: check if direct is OK here
                int received = channel.read (buffer);
                buffer.position (0);

                String  column  = "";
                boolean gotNL   = false;
                boolean inSpace = false;
                for (long i = 0; i < received; i++)
                {
                    char c = (char) buffer.get ();  // Technically, the file is in UTF-8, but this will only matter in column headings. We are looking for a float string, which will be in all lower ASCII.
                    if (c == '\n'  ||  c == '\r')
                    {
                        gotNL = true;
                        continue;
                    }
                    if (gotNL)
                    {
                        // Tabs and spaces should never be mixed in the output file.
                        boolean nextColumn = false;
                        if (c == '\t')
                        {
                            nextColumn = true;  // Tab always indicates next column.
                        }
                        else if (c == ' ')
                        {
                            if (inSpace) continue;
                            nextColumn = true;
                            inSpace = true;
                        }
                        else
                        {
                            inSpace = false;
                        }
                        if (nextColumn)
                        {
                            if (columnIndex == timeColumn)
                            {
                                try
                                {
                                    job.set (lineLength, "$metadata", "backend", "all", "lineLength");  // Remember most recent value, to more quickly track situation in the output stream.
                                    return Double.parseDouble (column);
                                }
                                catch (NumberFormatException e)
                                {
                                    // This is probably a column header rather than a value.
                                    // Skip to end of line and try again.
                                    gotNL = false;
                                    columnIndex = 0;
                                }
                            }
                            else
                            {
                                columnIndex++;
                            }
                        }
                        else
                        {
                            column = column + c;
                        }
                    }
                }
                lineLength *= 2;
            }
        }
        catch (Exception e) {}
        return 0;
    }
}
