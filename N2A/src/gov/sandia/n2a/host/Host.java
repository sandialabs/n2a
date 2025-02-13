/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.jobs.NodeJob;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
    Encapsulates access to a computer system, whether it is a workstation, a supercomputer or a "cloud" service.
    Services include:
    <ul>
    <li>File access
    <li>Process management (starting, monitoring, stopping)
    <li>Resource monitoring (memory, disk capacity, processor load)
    </ul>

    Hosts are described using key-value pairs stored in app state. Under each host is a list
    of backends and their host-specific configuration, such as the path to the C++ compiler.
    Additional keys include login information and network address.
**/
public abstract class Host
{
    public    String             name;                                    // Identifies host internally. Also acts as the default value of network address, but this can be overridden by the address key. This allows the use of a friendly name for display combined with, say, a raw IP for address.
    public    MNode              config;                                  // Collection of attributes that describe the target, including login information, directory structure and command forms. This should be a direct reference to node in app state, so any changes are recorded.
    protected ArrayList<NodeJob> running = new ArrayList<NodeJob> ();     // Jobs that we are actively monitoring because they may still be running.
    protected MonitorThread      monitorThread;
    public    Map<String,Object> objects = new HashMap<String,Object> (); // For other code to attach resources to a given host. Host itself does not use this collection.

    protected static Map<String,Host>     hosts     = new HashMap<String,Host> ();
    protected static List<ChangeListener> listeners = new ArrayList<ChangeListener> ();

    protected static ArrayList<NodeJob> waitingForHost = new ArrayList<NodeJob> ();
    protected static Semaphore          waitingAdded   = new Semaphore (0);  // Signals that something has been added to waitingForHost.
    protected static AssignmentThread   assignmentThread;

    protected static Set<PosixFilePermission> fullPermissions = new HashSet<PosixFilePermission> ();
    static
    {
        fullPermissions.add (PosixFilePermission.OWNER_READ);
        fullPermissions.add (PosixFilePermission.OWNER_WRITE);
        fullPermissions.add (PosixFilePermission.OWNER_EXECUTE);
        fullPermissions.add (PosixFilePermission.GROUP_READ);
        fullPermissions.add (PosixFilePermission.GROUP_WRITE);
        fullPermissions.add (PosixFilePermission.GROUP_EXECUTE);
        fullPermissions.add (PosixFilePermission.OTHERS_READ);
        fullPermissions.add (PosixFilePermission.OTHERS_WRITE);
        fullPermissions.add (PosixFilePermission.OTHERS_EXECUTE);
    }

    public interface Factory extends ExtensionPoint
    {
        public String className ();
        public Host   createInstance (); // not yet bound to app state
    }

    // Promises to make an instances of Remote
    public interface FactoryRemote extends Factory
    {
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

    public static void init ()
    {
        if (! hosts.isEmpty ()) return;

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

    public static Collection<Host> getHosts ()
    {
        init ();
        return hosts.values ();
    }

    public static String uniqueName ()
    {
        init ();
        String result = "newhost";
        int suffix = 2;
        while (hosts.containsKey (result)) result = "newhost" + suffix++;
        return result;
    }

    public static Host create (String hostname, String className)
    {
        Host h = Host.createHostOfClass (className);
        h.name = hostname;
        h.config = AppData.state.childOrCreate ("Host", hostname);  // This will bind to existing data, if there, so not a clean slate.
        h.config.set (h.getClassName (), "class");  // Even though class is default, we should be explicit in case default changes.
        hosts.put (h.name, h);
        h.restartMonitorThread ();
        notifyChange ();
        return h;
    }

    public static void rename (Host h, String newName)
    {
        hosts.remove (h.name);
        h.name = newName;
        hosts.put (newName, h);
    }

    public static void remove (Host h, boolean delete)
    {
        h.stopMonitorThread ();
        if (h instanceof Closeable)
        {
            try {((Closeable) h).close ();}
            catch (IOException error) {}
        }
        hosts.remove (h.name);
        if (delete) AppData.state.clear ("Host", h.name);
        notifyChange ();
    }

    public static Host get ()
    {
        init ();
        return hosts.get ("localhost");
    }

    public static Host get (String hostname)
    {
        init ();
        Host result = hosts.get (hostname);
        if (result == null) result = hosts.get ("localhost");
        return result;
    }

    public static Host get (MNode job)
    {
        return get (job.getOrDefault ("localhost", "host"));
    }

    @SuppressWarnings("resource")
    public static Host get (Path path)
    {
        if (path instanceof SshPath)
        {
            return get (((SshFileSystem) path.getFileSystem ()).connection.hostname);
        }
        return get ("localhost");
    }

    public static Host getByAddress (String address)
    {
        init ();
        for (Host h : hosts.values ())
        {
            if (h.config.getOrDefault (h.name, "address").equals (address)) return h;
        }
        return null;
    }

    public static void addChangeListener (ChangeListener l)
    {
        if (! listeners.contains (l)) listeners.add (l);
    }

    public static void removeChangeListener (ChangeListener l)
    {
        listeners.remove (l);
    }

    public static void notifyChange ()
    {
        ChangeEvent e = new ChangeEvent (hosts);  // The event object should be ignored.
        for (ChangeListener l : listeners) l.stateChanged (e);
    }

    public static void quit ()
    {
        if (assignmentThread != null) assignmentThread.stop = true;

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
                Connection.client.stop ();
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

    public static void restartAssignmentThread ()
    {
        if (assignmentThread != null) assignmentThread.stop = true;
        assignmentThread = new AssignmentThread ();
        assignmentThread.setDaemon (true);
        assignmentThread.start ();
    }

    public static void waitForHost (NodeJob job)
    {
        synchronized (waitingForHost)
        {
            waitingForHost.add (job);
            waitingAdded.release ();
        }
    }

    public static class AssignmentThread extends Thread
    {
        public boolean        stop;
        public Map<Host,Long> hostTime = new HashMap<Host,Long> ();

        public AssignmentThread ()
        {
            super ("Assign Jobs to Hosts");
        }

        public void run ()
        {
            init ();

            while (! stop)
            {
                // Sleep until we have something to work on.
                boolean empty;
                synchronized (waitingForHost)
                {
                    empty = waitingForHost.isEmpty ();
                    waitingAdded.drainPermits ();
                }
                if (empty)
                {
                    try {waitingAdded.acquire ();}
                    catch (InterruptedException e) {}
                }

                // Work on it.
                int i = 0;
                while (! stop)
                {
                    NodeJob job;
                    synchronized (waitingForHost)
                    {
                        if (i >= waitingForHost.size ()) break;
                        job = waitingForHost.get (i);
                        if (job.complete == 3  ||  job.deleted)  // The user terminated or deleted job before it got started.
                        {
                            job.complete = 4;
                            waitingForHost.remove (i);  // And don't bother sending to regular monitor.
                            continue;
                        }
                    }

                    // Find available host
                    MNode source = job.getSource ();
                    Backend backend = Backend.getBackend (source.get ("backend"));
                    String backendName = backend.getName ().toLowerCase ();

                    List<Host> candidates = new ArrayList<Host> ();
                    String hostNames = source.get ("host");
                    if (hostNames.isEmpty ())  // No host specified, so find suitable default.
                    {
                        // Prefer localhost, if backend is permitted.
                        Host    localhost = Host.get ("localhost");
                        boolean forbidden = localhost.config.get ("backend", backendName).equals ("0");
                        boolean internal  = backend instanceof InternalBackend;
                        if (internal  ||  ! forbidden)  // use of Internal overrides host selection
                        {
                            candidates.add (localhost);
                        }
                        else  // Find suitable remote host.
                        {
                            for (Host h : hosts.values ())
                            {
                                if (! (h instanceof Remote)) continue;  // localhost not permitted
                                if (h.config.get ("backend", backendName).equals ("0")) continue;  // forbidden
                                candidates.add (h);
                            }
                        }
                    }
                    else  // Only consider hosts specified by user.
                    {
                        for (String hostname : hostNames.split (","))
                        {
                            Host h = hosts.get (hostname.trim ());
                            if (h == null) continue;
                            if (h instanceof Remote  &&  backend instanceof InternalBackend) continue;  // Internal can only run on localhost.
                            if (h.config.get ("backend", backendName).equals ("0")) continue;  // marked as forbidden
                            candidates.add (h);
                        }
                    }
                    if (candidates.isEmpty ())  // No suitable hosts found.
                    {
                        // Report error
                        if (stop) return;  // Don't wait on locks if we are shutting down.
                        synchronized (waitingForHost) {waitingForHost.remove (i);}
                        Path localJobDir = getJobDir (getLocalResourceDir (), source);
                        try
                        {
                            synchronized (job)
                            {
                                job.complete = 2;
                                Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), localJobDir.resolve ("finished"));
                            }
                            // Reopen the err stream and append an explanation.
                            try (PrintStream err = new PrintStream (new FileOutputStream (localJobDir.resolve ("err").toFile (), true), false, "UTF-8"))
                            {
                                err.println ("ERROR: Backend can't run on given host, and no suitable alternative was found.");
                            }
                        }
                        catch (Exception e) {}
                        continue;
                    }

                    Host chosenHost = null;
                    for (Host h : candidates)
                    {
                        // Enable host, but only for newly-launched jobs.
                        // If the job pre-existed the current session of this app,
                        // then wait for the user to explicitly enable the host.
                        if (! job.old  &&  h instanceof Remote)
                        {
                            job.old = true;  // Only allow ourselves this privilege once. Otherwise, user could get hammered with login prompts.
                            ((Remote) h).enable ();
                        }

                        // Throttle runs on the same host, so each has time to allocate resources
                        // before the next one starts.
                        if (stop) return;
                        Long previous = hostTime.get (h);
                        if (previous != null)
                        {
                            long elapsed = System.currentTimeMillis () - previous;
                            long wait = 1000 - elapsed;
                            try {if (wait > 0) sleep (wait);}
                            catch (InterruptedException e) {}
                        }

                        if (stop) return;
                        hostTime.put (h, System.currentTimeMillis ());  // Don't poll a host more than one per second, regardless of whether or not a job is started.
                        if (backend.canRunNow (h, source))
                        {
                            chosenHost = h;
                            break;
                        }
                    }
                    if (chosenHost == null)  // No host ready, so move on to next job.
                    {
                        i++;
                        continue;
                    }

                    // Host is ready, so start job and move to host's monitor list.
                    if (stop) return;
                    source.set (chosenHost.name, "host");
                    backend.start (source);
                    hostTime.put (chosenHost, System.currentTimeMillis ());  // Remember when the most recent job was started on the chosen host.
                    synchronized (waitingForHost) {waitingForHost.remove (i);}
                    synchronized (chosenHost.running) {chosenHost.running.add (job);}
                }
            }
        }
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

    public void restartMonitorThread ()
    {
        if (monitorThread != null) monitorThread.stop = true;
        monitorThread = new MonitorThread ();
        monitorThread.setDaemon (true);
        monitorThread.start ();
    }

    public void stopMonitorThread ()
    {
        if (monitorThread == null) return;
        monitorThread.stop = true;
        monitorThread = null;
    }

    public void transferJobsTo (Host h)
    {
        synchronized (running)
        {
            synchronized (h.running)
            {
                for (NodeJob job : running) h.running.add (job);
            }
            running.clear ();
        }
    }

    public void monitor (NodeJob job)
    {
        synchronized (running) {running.add (job);}
    }

    public void unmonitor (NodeJob job)
    {
        synchronized (running) {running.remove (job);}
    }

    /**
        @return A snapshot of the current list of running jobs.
        This list is independent of the one used by Host, so it will
        not continue to be updated after this function returns, nor will
        modifications to it impact job monitoring.
    **/
    public List<NodeJob> getRunning ()
    {
        synchronized (running) {return new ArrayList<NodeJob> (running);}
    }

    public class MonitorThread extends Thread
    {
        public boolean stop;

        public MonitorThread ()
        {
            super ("Monitor " + name);
        }

        public void run ()
        {
            // Periodic refresh to show status of running jobs
            while (! stop)
            {
                if (running.isEmpty ())
                {
                    try {sleep (1000);}
                    catch (InterruptedException e) {}
                }

                int i = 0;
                while (! stop)
                {
                    NodeJob job;
                    synchronized (running)
                    {
                        if (i >= running.size ()) break;
                        job = running.get (i);
                    }
                    job.monitorProgress ();  // Contains built-in throttling, so only one check per second.
                    if (job.complete >= 1  &&  job.complete != 3  ||  job.deleted)
                    {
                        // If necessary, we can use a more efficient method to remove
                        // the element (namely, overwrite the ith element with the back element).
                        synchronized (running) {running.remove (i);}
                    }
                    else
                    {
                        i++;
                    }
                }
            }
        }
    }

    /**
        Used to show this host in a list for editing.
    **/
    public String toString ()
    {
        return name;
    }

    public String getClassName ()
    {
        return getClass ().getSimpleName ();
    }

    /**
        Construct a panel for editing this class of host.
        The editor will be bound to the relevant record in application state.
    **/
    public JPanel getEditor ()
    {
        return new JPanel ();
    }

    public abstract boolean           isAlive        (MNode job)                                                                  throws Exception;  // Checks if the system process associated with the job still exists and (where possible) is not a zombie.
    public abstract List<ProcessInfo> getActiveProcs ()                                                                           throws Exception;  // enumerate all of our active jobs
    public abstract void              submitJob      (MNode job, boolean out2err, List<List<String>> command, List<Path> libPath) throws Exception;  // Job consists of a sequence of commands. Generally this will get converted to a shell script on the host, with the reserved name "n2a_job".
    public abstract void              killJob        (MNode job, boolean force)                                                   throws Exception;

    public void submitJob (MNode job, boolean out2err, String... command) throws Exception
    {
        List<List<String>> commands = new ArrayList<List<String>> ();
        commands.add (Arrays.asList (command));
        submitJob (job, out2err, commands, null);
    }

    /**
        Indicates that the scheduling system on this host writes extra information to stdout.
        This can interfere with output from the simulation itself, so the backend should take
        measures to write a separate output file. It is also helpful to set out2err in the
        submitJob() call, so that all diagnostic information ends up in err.
    **/
    public boolean clobbersOut ()
    {
        return false;
    }

    /**
        Indicates that the scheduling system on this host has its own special command for
        starting parallel jobs, so the backend should not use a special script/command
        for this.
        Examples of scheduling-system run commands include: srun, jsrun.
        Examples of parallel run commands include: mpiexec, mpirun, charmrun.
    **/
    public boolean hasRun ()
    {
        return false;
    }

    public String shellSuffix ()
    {
        return "";
    }

    public class ProcessInfo
    {
        public long   pid;
        public String jobKey;     // unique ID of job directory
        public long   memory;     // bytes in use
        public double cpu = 1;    // number of cores in use
        public String state = ""; // for HPC jobs
    }

    /**
        A general process-building interface that allows the caller to work with
        both ProcessBuilder and RemoteProcessBuilder.
    **/
    public static interface AnyProcessBuilder
    {
        public AnyProcessBuilder  redirectInput  (Path file);  // will pipe file to the process stdin
        public AnyProcessBuilder  redirectOutput (Path file);  // will pipe process stdout to the file
        public AnyProcessBuilder  redirectError  (Path file);  // will pipe process stderr to the file
        public Map<String,String> environment ();
        /**
            Construct and start the process.
            This is the only function that needs to be inside the try-with-resources.
        **/
        public AnyProcess start () throws IOException;
    }

    /**
        A general process that presents the Closeable interface.
        This allows a standard Process to be used in a try-with-resources, just
        like a RemoteProcess.
    **/
    public static interface AnyProcess extends Closeable
    {
        public OutputStream getOutputStream ();  // pipes to the process stdin
        public InputStream  getInputStream  ();  // pipes from the process stdout
        public InputStream  getErrorStream  ();  // pipes from the process stderr
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

        public Map<String,String> environment ()
        {
            return builder.environment ();
        }

        public AnyProcess start () throws IOException
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
        if (AppData.properties.getBoolean ("headless")  &&  ! (resourceDir instanceof SshPath)) return Paths.get (job.get ()).getParent ();
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
        Concatenates command into a single string suitable for use in shell scripts on this host.
    **/
    public String combine (String... command)
    {
        String result = "";
        if (command.length > 0) result = command[0];
        for (int i = 1; i < command.length; i++) result += " " + command[i];
        return result;
    }

    public String combine (List<String> command)
    {
        return combine (command.toArray (new String[command.size ()]));
    }

    /**
        Finds the absolute locates of an executable file by walking the search path
        (typically specified by the PATH environment variable).
        This default implementation is suitable for any localhost, but should be
        overridden for remote hosts.
        @return null if not found
    **/
    public Path which (Path command)
    {
        if (command.isAbsolute ()) return command;

        Path exe = null;
        if (this instanceof Windows)
        {
            String fileName = command.getFileName ().toString ();
            if (! fileName.endsWith (".exe"))
            {
                exe = Paths.get (fileName + ".exe");
                Path parent = command.getParent ();
                if (parent != null) exe = parent.resolve (exe);
            }
        }

        String PATH = System.getenv ("PATH");
        String separator = System.getProperty ("path.separator");
        for (String entry : PATH.split (separator))
        {
            Path dir = Paths.get (entry);
            Path p = dir.resolve (command);
            if (Files.exists (p)) return p;
            if (exe != null)
            {
                p = dir.resolve (exe);
                if (Files.exists (p)) return p;
            }
        }

        return null;
    }

    public long getMemoryTotal ()
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

    public long getMemoryFree ()
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

    /**
        @return Number of processors available to use. This is directly comparable to the result of getProcessorTotal().
        If the system has 32 processors and they are all 75% idle, then the return value is 24.0.
    **/
    public double getProcessorIdle ()
    {
        int nproc = getProcessorTotal ();
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return nproc - (Double) invoke (OS, "getSystemCpuLoad");
        }
        catch (Exception e)
        {
            // Fallback: use a function from the general interface, rather than hoping for
            // a com.sun implementation.
            return nproc - Math.max (0, OS.getSystemLoadAverage ());  // TODO: known to fail on Windows
        }
    }

    public long getDiskFree ()
    {
        try
        {
            return Files.getFileStore (getResourceDir ()).getUsableSpace ();
        }
        catch (Exception e)
        {
            return 0;
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

    /**
        Reads a limited number of bytes from the given input stream and writes them to the given output stream.
    **/
    public static long copy (InputStream in, OutputStream out, long maximum, CopyProgress progress) throws IOException
    {
        long total = 0;
        long lastProgress = 0;
        long remaining = maximum;
        byte[] buffer = new byte[8192];  // 8kiB
        int n;
        while ((n = in.read (buffer, 0, (int) Math.min (buffer.length, remaining))) > 0)
        {
            out.write (buffer, 0, n);
            total += n;
            remaining -= n;  // Since read length should never exceed remaining, this should never go negative.

            if (progress != null  &&  (double) (total - lastProgress) / maximum > 0.01)
            {
                lastProgress = total;
                double percent = (double) total / maximum;
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        progress.update ((float) percent);
                    }
                });
            }
        }
        return total;
    }

    public static long copy (InputStream in, OutputStream out, long maximum) throws IOException
    {
        return copy (in, out, maximum, null);
    }

    public interface CopyProgress
    {
        /**
            Notify of change in status. This function is always called on the EDT,
            so the implementer is free to update the gui.
        **/
        public void update (float percent);
    }

    public static void stringToFile (String value, Path path) throws IOException
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
        catch (IOException e)
        {
            return 0;
        }
    }

    /**
        Same as Files.size() but doesn't throw an exception if file does not exist.
        Instead, returns 0.
    **/
    public static long size (Path path)
    {
        try
        {
            return Files.size (path);
        }
        catch (IOException e)
        {
            return 0;
        }
    }

    /**
        Deletes the file or directory and all of its descendants.
        Any exceptions are silently ignored.
    **/
    public void deleteTree (Path start)
    {
        // Generic implementation. Works even for remote filesystems.
        new DeleteTreeVisitor (start).walk ();
    }

    public static class DeleteTreeVisitor extends SimpleFileVisitor<Path>
    {
        public Path    start;
        public boolean setPermissions = false;
        public boolean evilNIO;

        public DeleteTreeVisitor (Path start)
        {
            this.start = start;

            // On Windows, the JVM sometimes holds file locks even after we close the file.
            // This can keep us from being able to delete directories.
            // Garbage collection helps reduce this problem, though it does not guarantee success.
            evilNIO = isWindows ();
        }

        public void walk ()
        {
            if (evilNIO) System.gc ();

            try {Files.walkFileTree (start, this);}
            catch (IOException e) {}

            if (evilNIO  &&  setPermissions) System.gc ();  // To get rid of files opened while setting permissions.
        }

        public FileVisitResult visitFile (final Path file, final BasicFileAttributes attrs) throws IOException
        {
            try
            {
                Files.delete (file);
            }
            catch (AccessDeniedException ade)
            {
                if (makeDeletable (file)) Files.delete (file);
            }
            return FileVisitResult.CONTINUE;
        }

        public FileVisitResult postVisitDirectory (final Path dir, final IOException e) throws IOException
        {
            try
            {
                if (evilNIO  &&  setPermissions)
                {
                    System.gc ();
                    setPermissions = false;
                }
                Files.delete (dir);
            }
            catch (AccessDeniedException ade)
            {
                if (makeDeletable (dir)) Files.delete (dir);
            }
            return FileVisitResult.CONTINUE;
        }

        public boolean makeDeletable (Path file) throws IOException
        {
            DosFileAttributeView dosView = Files.getFileAttributeView (file, DosFileAttributeView.class);
            if (dosView != null)
            {
                dosView.setReadOnly (false);
                setPermissions = true;
                return true;
            }

            PosixFileAttributeView posixView = Files.getFileAttributeView (file, PosixFileAttributeView.class);
            if (posixView != null)
            {
                posixView.setPermissions (fullPermissions);
                setPermissions = true;
                return true;
            }

            return false;
        }
    }
}
