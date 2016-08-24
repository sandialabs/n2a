/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationException;

public class Wrapper extends Part
{
    public Simulator simulator;

    public Wrapper (EquationSet model)
    {
        if (model.connectionBindings != null) throw new EvaluationException ("Only compartments may be top-level models.");  // What are you going to connect, anyway?
        populations = new Population[1];
        populations[0] = new PopulationCompartment (model, this);
    }

    public void init (Simulator simulator)
    {
        populations[0].init (simulator);
        simulator.writeTrace ();
    }

    public void integrate (Simulator simulator)
    {
        populations[0].integrate (simulator);
    }

    public void update (Simulator simulator)
    {
        populations[0].update (simulator);
    }

    public boolean finish (Simulator simulator)
    {
        populations[0].finish (simulator);
        boolean result = ((PopulationCompartment) populations[0]).n > 0;
        if (result) simulator.writeTrace ();  // only trace if sim is still running
        return result;
    }
}
