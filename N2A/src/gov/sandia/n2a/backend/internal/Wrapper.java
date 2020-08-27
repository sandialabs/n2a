/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationException;

public class Wrapper extends Part
{
    public Wrapper (EquationSet model)
    {
        if (model.connectionBindings != null) throw new EvaluationException ("Only compartments may be top-level models.");  // What are you going to connect, anyway?
        valuesObject = new Object[1];
        valuesObject[0] = new Population (model, this);
    }

    public void init (Simulator simulator)
    {
        ((Population) valuesObject[0]).init (simulator);
    }

    public void integrate (Simulator simulator)
    {
        ((Population) valuesObject[0]).integrate (simulator);
    }

    public void update (Simulator simulator)
    {
        ((Population) valuesObject[0]).update (simulator);
    }

    public boolean finish (Simulator simulator)
    {
        Population p = (Population) valuesObject[0];
        p.finish (simulator);
        return p.n > 0;
    }
}
