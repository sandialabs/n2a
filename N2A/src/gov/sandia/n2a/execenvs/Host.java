/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.jcraft.jsch.JSchException;

/**
    Encapsulates access to a computer system, whether it is a workstation, a supercomputer or a "cloud" service.
    Services include:
    <ul>
    <li>File access
    <li>Process management (starting, monitoring, stopping)
    <li>Resource monitoring (memory, disk capacity, processor load)
    </ul>

    Hosts are described using key-value pairs stored in app state. Names of backends are reserved
    as top-level keys. Under these are specific configurations for the Backend X Host combination,
    such as the path to the C++ compiler. Additional top-level keys include login information
    and network address. All these should be documented in the online manual.
**/
public abstract class Host
{
    public String name;   // Identifies host internally. Also acts as the default value of network address, but this can be overridden by the hostname key. This allows the use of a friendly name for display combined with, say, a raw IP for address.
    public MNode  config; // Collection of attributes that describe the target, including login information, directory structure and command forms. This should be a direct reference to node in app state, so any changes are recorded.

    public static int              jobCount = 0;
    public static Map<String,Host> hosts    = new HashMap<String,Host> ();
    static
    {
        Host localhost;
        if (isWindows ()) localhost = new Windows ();
        else              localhost = new Unix ();  // Should be compatible with Mac bash shell.
        localhost.name   = "localhost";
        localhost.config = AppData.state.childOrCreate ("Host", "localhost");
        hosts.put (localhost.name, localhost);

        // Load configured remote hosts from app data
        for (MNode config : AppData.state.childOrEmpty ("Host"))
        {
            String name = config.key ();
            if (name.equals ("localhost")) continue;
            String className = config.get ("class");
            Host hs = createHostOfClass (className);
            hs.name = name;
            hs.config = config;
            hosts.put (name, hs);
        }
    }

    public interface Factory extends ExtensionPoint
    {
        public String className ();
        public Host   createInstance (); // not yet bound to app state
    }

    public static Host createHostOfClass (String className)
    {
        for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Factory.class))
        {
            Factory f = (Factory) ext;
            if (f.className ().equalsIgnoreCase (className)) return f.createInstance ();
        }
        return new RemoteUnix ();  // Note that localhost is always determined by direct probe of our actual OS.
    }

    public static Host get (String hostname)
    {
        Host result = hosts.get (hostname);
        if (result == null) result = hosts.get ("localhost");
        return result;
    }

    public static Host get (MNode job)
    {
        return get (job.getOrDefault ("localhost", "$metadata", "host"));
    }

    public static Host getByAddress (String address)
    {
        for (Host h : hosts.values ())
        {
            if (h.config.getOrDefault (h.name, "address").equals (address)) return h;
        }
        return null;
    }

    public static void quit ()
    {
        Thread shutdownThread = new Thread ("Close Host connections")
        {
            public void run ()
            {
                for (Host h : hosts.values ())
                {
                    if (h instanceof Closeable)
                    {
                        try {((Closeable) h).close ();}
                        catch (IOException e) {}
                    }
                }
            }
        };
        shutdownThread.setDaemon (true);
        shutdownThread.start ();

        // 500ms is a generous amount of time to wait for graceful shutdown of one connection
        // Limit total to 3s so that closing the program does not take an absurd amount of time.
        int waitTime = Math.max (3000, 500 * hosts.size ());
        try
        {
            shutdownThread.join (waitTime);
        }
        catch (InterruptedException e) {}
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

    public abstract boolean   isActive       (MNode job)                 throws Exception;  // check if the given job is active
    public abstract Set<Long> getActiveProcs ()                          throws Exception;  // enumerate all of our active jobs
    public abstract void      submitJob      (MNode job, String command) throws Exception;
    public abstract void      killJob        (MNode job, boolean force)  throws Exception;

    /**
        A general process-building interface that allows the caller to work with
        both ProcessBuilder and RemoteProcessBuilder.
    **/
    public static interface AnyProcessBuilder
    {
        public AnyProcessBuilder redirectInput  (Path file);
        public AnyProcessBuilder redirectOutput (Path file);
        public AnyProcessBuilder redirectError  (Path file);
        /**
            Construct and start the process.
            This is the only function that needs to be inside the try-with-resources.
        **/
        public AnyProcess start () throws Exception;
    }

    /**
        A general process that presents the Closeable interface.
        This allows a standard Process to be used in a try-with-resources, just
        like a RemoteProcess.
    **/
    public static interface AnyProcess extends Closeable
    {
        public OutputStream getOutputStream ();
        public InputStream  getInputStream  ();
        public InputStream  getErrorStream  ();
        public int          waitFor         ()                            throws InterruptedException;
        public boolean      waitFor         (long timeout, TimeUnit unit) throws InterruptedException;
        public int          exitValue       ()                            throws IllegalThreadStateException;
        public void         destroy         ();
        public AnyProcess   destroyForcibly ();
        public boolean      isAlive         ();
    }

    /**
        Wrapper for ProcessBuilder.
    **/
    public static class LocalProcessBuilder implements AnyProcessBuilder
    {
        protected ProcessBuilder builder;

        public LocalProcessBuilder (String... command)
        {
            builder = new ProcessBuilder (command);
        }

        public AnyProcessBuilder redirectInput (Path file)
        {
            builder.redirectInput (file.toFile ());
            return this;
        }

        public AnyProcessBuilder redirectOutput (Path file)
        {
            builder.redirectOutput (file.toFile ());
            return this;
        }

        public AnyProcessBuilder redirectError (Path file)
        {
            builder.redirectError (file.toFile ());
            return this;
        }

        public AnyProcess start () throws IOException, JSchException
        {
            return new LocalProcess (builder.start ());
        }
    }

    /**
        Wrapper for Process that allows it to be used in a try-with-resources.
    **/
    public static class LocalProcess implements AnyProcess
    {
        protected Process process;

        public LocalProcess (Process process)
        {
            this.process = process;
        }

        public void close () throws IOException
        {
        }

        public OutputStream getOutputStream ()
        {
            return process.getOutputStream ();
        }

        public InputStream getInputStream ()
        {
            return process.getInputStream ();
        }

        public InputStream getErrorStream ()
        {
            return process.getErrorStream ();
        }

        public int waitFor () throws InterruptedException
        {
            return process.waitFor ();
        }

        public boolean waitFor (long timeout, TimeUnit unit) throws InterruptedException
        {
            return process.waitFor (timeout, unit);
        }

        public int exitValue () throws IllegalThreadStateException
        {
            return process.exitValue ();
        }

        public void destroy ()
        {
            process.destroy ();
        }

        public AnyProcess destroyForcibly ()
        {
            process.destroyForcibly ();
            return this;
        }

        public boolean isAlive ()
        {
            return process.isAlive ();
        }
    }

    /**
        Creates either a local or remote process that executes the given command line.
        Unless this process is known to be local, it should be wrapped in a try-with-resources
        so that a RemoteProcess will get properly closed.
    **/
    public AnyProcessBuilder build (String... command) throws Exception
    {
        // This default implementation is for the local machine.
        return new LocalProcessBuilder (command);
    }

    public AnyProcessBuilder build (List<String> command) throws Exception
    {
        return build (command.toArray (new String[command.size ()]));
    }

    /**
        Determines the application data directory on the system where a job is executed.
        In the case of a remote host, the Path object will give NIO access to the remote file system.
        In the case of localhost, this is the same as local resource dir.
    **/
    public Path getResourceDir () throws Exception
    {
        return getLocalResourceDir ();  // Only suitable for localhost. Must override for remote systems.
    }

    /**
        Determines the application data directory on the system where a job is managed.
        This directory can store/provide information about a job even when a remote host is not connected.
    **/
    public static Path getLocalResourceDir ()
    {
        return Paths.get (AppData.properties.get ("resourceDir"));
    }

    public static Path getJobDir (Path resourceDir, MNode job)
    {
        return resourceDir.resolve ("jobs").resolve (job.key ());
    }

    /**
        When needed, wraps the given path in quotes so it won't get misread by the target shell.
    **/
    public String quote (Path path)
    {
        // Default for local machine is not to quote, because we use the more advanced ProcessBuilder
        // class, which allows separate args.
        return path.toString ();
    }

    /**
        Determine memory usage for a given job.
    **/
    public abstract long getProcMem (long pid) throws Exception;

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

    public static void downloadFile (Path remotePath, Path localPath) throws Exception
    {
        Files.copy (remotePath, localPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void stringToFile (Path path, String value) throws IOException
    {
        // Writes string as UTF-8
        try (BufferedWriter writer = Files.newBufferedWriter (path))
        {
            writer.write (value);
        }
    }

    public static String fileToString (Path path)
    {
        try (InputStream fis = Files.newInputStream (path))
        {
            return streamToString (fis);
        }
        catch (IOException e)
        {
            return "";
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

    public static void deleteTree (Path start, boolean includeStartDir)
    {
        // On Windows, the JVM sometimes holds file locks even after we close the file.
        // This can keep us from being able to delete directories.
        // Garbage collection helps reduce this problem, though it does not guarantee success.
        System.gc ();

        try
        {
            Files.walkFileTree (start, new SimpleFileVisitor<Path> ()
            {
                public FileVisitResult visitFile (final Path file, final BasicFileAttributes attrs) throws IOException
                {
                    Files.delete (file);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory (final Path dir, final IOException e) throws IOException
                {
                    if (includeStartDir  ||  ! dir.equals (start)) Files.delete (dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {}
    }
}
