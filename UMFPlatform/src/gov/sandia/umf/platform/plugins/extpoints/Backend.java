/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import java.io.PrintStream;

import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import replete.plugins.ExtensionPoint;

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
    public static class AbortRun extends Exception
    {
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
    public void execute (MNode job)
    {
    }

    /**
        Determine how much sim time the simulation will take.
        @return Expected sim time in seconds, or zero if unable to estimate.
        Note: Infinity is a possible answer, if the model is intended for continuous operation.
    **/
    public double expectedDuration (MNode job)
    {
        return 0;
    }

    /**
        Return an estimate of the current $t in the active simulation.
    **/
    public double currentSimTime (MNode job)
    {
        return 0;
    }
}
