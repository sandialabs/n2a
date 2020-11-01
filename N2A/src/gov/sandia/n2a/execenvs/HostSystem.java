/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
    Encapsulates access to a computer system, whether it is a workstation, a supercomputer or a "cloud" service.
    Services include:
    <ul>
    <li>File access
    <li>Process management (starting, monitoring, stopping)
    <li>Resource monitoring (memory, disk capacity, processor load)
    </ul>
**/
public abstract class HostSystem
{
    public String name;  // Simple handle used internally. A nickname in the case of remote systems. Note that IP address or host name is specified separately.

    protected static Map<String,HostSystem> hosts    = new HashMap<String,HostSystem> ();
    protected static int                    jobCount = 0;

    public static HostSystem get (String hostname)
    {
        // Lazy initialization of host collection
        if (hosts.isEmpty ())
        {
            HostSystem localhost;
            if (isWindows ()) localhost = new Windows ();
            else              localhost = new Linux ();
            localhost.name = "localhost";
            hosts.put (localhost.name, localhost);

            // TODO: load configured remote hosts from app data
        }

        HostSystem result = hosts.get (hostname);
        if (result == null) result = hosts.get ("localhost");
        return result;
    }

    /**
        Determines if this application is running on a Windows system.
        Not to be confused with the type of system a particular job executes on.
    **/
    public static boolean isWindows ()
    {
        return System.getProperty ("os.name").toLowerCase ().indexOf ("win") >= 0;
    }

    /**
        Determines if this application is running on a Mac system.
        Not to be confused with the type of system a particular job executes on.
    **/
    public static boolean isMac ()
    {
        return System.getProperty ("os.name").toLowerCase ().indexOf ("mac") >= 0;
    }

    // TODO: This interface should use NIO as much as possible.
    // In particular, remote file access should be encapsulated in a FileSystemProvider.

    public abstract boolean   isActive        (MNode job)                   throws Exception;  // check if the given job is active
    public abstract Set<Long> getActiveProcs  ()                            throws Exception;  // enumerate all of our active jobs
    public abstract long      getProcMem      (long pid)                    throws Exception;  // determine memory usage for the given job
    public abstract void      submitJob       (MNode job, String command)   throws Exception;
    public abstract void      killJob         (long pid, boolean force)     throws Exception;
    public abstract void      setFileContents (String path, String content) throws Exception;
    public abstract String    getFileContents (String path)                 throws Exception;
    public abstract void      deleteJob       (String jobName)              throws Exception;
    public abstract void      downloadFile    (String path, File destPath)  throws Exception;

    public static long lastModified (Path path)
    {
        try
        {
            return Files.getLastModifiedTime (path).toMillis ();
        }
        catch (IOException e1)
        {
            return 0;
        }
    }

    public String file (String dirName, String fileName) throws Exception
    {
        return new File (dirName, fileName).getAbsolutePath ();
    }

    public String quotePath (Path path)
    {
        return "'" + path + "'";
    }

    public String getNamedValue (String name)
    {
        return getNamedValue (name, "");
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) {
            return "Generic";
        }
        return defaultValue;
    }

    @Override
    public String toString()
    {
        return getNamedValue ("name");
    }

    public long getMemoryPhysicalTotal ()
    {
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return (Long) invoke (OS, "getTotalPhysicalMemorySize");
        }
        catch (Exception e)
        {
            System.out.println ("Lack getTotalPhysicalMemorySize call");
            return 0;
        }
    }

    public long getMemoryPhysicalFree ()
    {
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return (Long) invoke (OS, "getFreePhysicalMemorySize");
        }
        catch (Exception e)
        {
            System.out.println ("Lack getFreePhysicalMemorySize call");
            return 0;
        }
    }

    public int getProcessorTotal ()
    {
        // We will assume that processors available to JVM are exactly the same as total system processors.
        return Runtime.getRuntime ().availableProcessors ();
    }

    public double getProcessorLoad ()
    {
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return (Long) invoke (OS, "getSystemCpuLoad");
        }
        catch (Exception e)
        {
            return OS.getSystemLoadAverage ();  // TODO: known to fail on Windows
        }
    }

    // Utility functions -----------------------------------------------------

    public static Object invoke (Object target, String methodName, Object... args)
    {
        Class<?> clazz = target.getClass ();
        try
        {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for (int a = 0; a < args.length; a++) parameterTypes[a] = args[a].getClass ();
            Method m = clazz.getMethod (methodName, parameterTypes);
            m.setAccessible (true);
            return m.invoke (target, args);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static void stringToFile (File target, String value) throws IOException
    {
        try (FileOutputStream fos = new FileOutputStream (target))
        {
            fos.write (value.getBytes ("UTF-8"));
        }
    }

    public static String streamToString (InputStream input)
    {
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (input, "UTF-8")))
        {
            return reader.lines ().collect (Collectors.joining ("\n"));
        }
        catch (IOException e)
        {
            return "";
        }
    }

    public static String fileToString (File input)
    {
        try (FileInputStream fis = new FileInputStream (input))
        {
            return streamToString (fis);
        }
        catch (IOException e)
        {
            return "";
        }
    }
}
