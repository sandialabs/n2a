/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import replete.plugins.ExtensionPoint;

public interface Backend extends ExtensionPoint
{
    /**
        Returns the display name for this back-end.
    **/
    public String getName ();

    /**
        Give the list of parameters that can be used to configure the
        simulator during an actual simulation.
    **/
    public ParameterDomain getSimulatorParameters ();

    /**
        Enumerate all the variables that could be named in an output expression.
        Output expressions produce diagnostic information that is dumped during
        an actual simulation.
    **/
    public ParameterDomain getOutputVariables (MNode model);

    /**
        Indicates whether the given parameter can be varied within the simulation.
        If not, we must launch multiple simulations to vary it.
        @return true if the parameter can be varied directly within the simulation
    **/
    public boolean canHandleRunEnsembleParameter (MNode model, Object key, ParameterSpecification spec);

    /**
        Indicates that resources are available to execute the job.
        This requires knowledge of three things:
        * The specifics of the job (how big the model will be).
        * The nature of the specific backend, such as how much resources it needs to handle the given model size.
        * The target machine (available memory, CPUs, etc.) Information needed to determine this should be embedded in the job metadata.
    **/
    public boolean canRunNow (MNode job);

    /**
        For a local machine, start the process that actually computes the job.
        For a remote system, submit the job to whatever scheduler may exist there.
    **/
    public void execute (MNode job) throws Exception;
}
