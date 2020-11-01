/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.parms.ParameterDomain;


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
        Give the list of parameters that can be used to configure the
        simulator during an actual simulation.
    **/
    public ParameterDomain getSimulatorParameters ()
    {
        return null;
    }

    /**
        Enumerate all the variables that could be named in an output expression.
        Output expressions produce diagnostic information that is dumped during
        an actual simulation.
    **/
    public ParameterDomain getOutputVariables (MNode model)
    {
        return null;
    }

    /**
        Indicates whether the given parameter can be varied within the simulation.
        If not, we must launch multiple simulations to vary it.
        @return true if the parameter can be varied directly within the simulation
    **/
    public boolean canHandleRunEnsembleParameter (MNode model, Object key, ParameterSpecification spec)
    {
        return false;
    }

    /**
        Indicates that resources are available to execute the job.
        This requires knowledge of three things:
        * The specifics of the job (how big the model will be).
        * The nature of the specific backend, such as how much resources it needs to handle the given model size.
        * The target machine (available memory, CPUs, etc.) Information needed to determine this should be embedded in the job metadata.
    **/
    public boolean canRunNow (MNode job)
    {
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
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid != 0)
        {
            try
            {
                HostSystem.get (job.getOrDefault ("localhost", "$metadata", "host")).killJob (pid, force);
                Path jobDir = Paths.get (job.get ()).getParent ();
                Files.copy (new ByteArrayInputStream ("killed" .getBytes ("UTF-8")), jobDir.resolve ("finished"));
                // Note that BackendC on Windows uses the mere existence of the "finished" file as a signal to shut down gracefully.
                // This is due to the lack of a proper SIGTERM in Windows.
            }
            catch (Exception e) {}
        }
    }

    /**
        Return an estimate of the current $t in the active simulation.
    **/
    public double currentSimTime (MNode job)
    {
        return 0;
    }
}
