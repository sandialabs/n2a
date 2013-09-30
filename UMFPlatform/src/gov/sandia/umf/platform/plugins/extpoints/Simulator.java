/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import replete.plugins.ExtensionPoint;

public interface Simulator extends ExtensionPoint
{
    /**
        Returns the display name for this back-end.
    **/
    public String getName ();

    /**
        Returns the names of all model types that this back-end can handle.
        This is not the name of the simulator, but rather than name of the kind of
        thing it can process.
    **/
    public String[] getCompatibleModelTypes ();

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
    public ParameterDomain getOutputVariables (Object model);

    /**
        Indicates whether the given parameter can be varied within the simulation.
        If not, we must launch multiple simulations to vary it.
        @return true if the parameter can be varied directly within the simulation
    **/
    public boolean canHandleRunEnsembleParameter (Object model, Object key, ParameterSpecification spec);

    /**
        Instantiate a specific simulation.
        @todo There should be only two interfaces that a back-end deals with.
        One is the ExtensionPoint (this class), and the other is a instance of a simulation.
        Right now we have an extra layer called "Simulation". This should be refactored into
        RunState and Simulator. Simulator should be stateless, and contain all methods that
        have to do with constructing a simulation. RunState should contain all state that
        is simulation-specific. 
    **/
    public Simulation createSimulation ();
}
