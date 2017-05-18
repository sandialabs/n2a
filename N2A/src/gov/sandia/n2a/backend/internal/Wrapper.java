/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.Map.Entry;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.function.Output;

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
        for (Entry<String,Output.Holder> h : simulator.outputs.entrySet ()) h.getValue ().writeTrace ();
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
        if (result)  // only trace if sim is still running
        {
            for (Entry<String,Output.Holder> h : simulator.outputs.entrySet ()) h.getValue ().writeTrace ();
        }
        return result;
    }
}
