/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

public class SimulatorC implements Simulator
{
    @Override
    public String getName ()
    {
        return "C";
    }

    @Override
    public String[] getCompatibleModelTypes ()
    {
        return new String[] {"n2a"};
    }

    @Override
    public ParameterDomain getSimulatorParameters ()
    {
        ParameterDomain result = new ParameterDomain ();
        result.addParameter (new Parameter ("duration",     "1.0"  ));  // default is 1 second
        result.addParameter (new Parameter ("c.integrator", "Euler"));  // alt is "RungeKutta"
        return result;
    }

    @Override
    public ParameterDomain getOutputVariables (Object model)
    {
        try
        {
            MNode n = (MNode) model;
            if (n == null) return null;
            EquationSet s = new EquationSet (n);
            if (s.name.length () < 1) s.name = "Model";
            s.resolveLHS ();
            return s.getOutputParameters ();
        }
        catch (Exception error)
        {
            return null;
        }
    }

    @Override
    public Simulation createSimulation ()
    {
        return new SimulationC ();
    }

    @Override
    public boolean canHandleRunEnsembleParameter (Object model, Object key, ParameterSpecification spec)
    {
        // For now, all parameter variation must be outside the generated C code.
        return false;
    }
}
