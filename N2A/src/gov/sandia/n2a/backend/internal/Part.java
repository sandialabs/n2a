/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.type.Instance;

/**
    An Instance that is capable of holding sub-populations.
    Generally, this is any kind of Instance except a Population.
**/
public class Part extends Instance
{
    public Population[] populations;

    public Part (EquationSet equations, Instance container)
    {
        this.equations = equations;
        this.container = container;
        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;
        allocate (bed.countLocalFloat, bed.countLocalType);
        if (equations.parts.size () > 0)
        {
            populations = new Population[equations.parts.size ()];
            int i = 0;
            for (EquationSet s : equations.parts)
            {
                populations[i++] = new Population (s, this);
            }
        }
    }

    public void die ()
    {
        // remove self from population
        // set $live to false
        // update accountable endpoints (if connection)
    }

    public void init (Euler simulator)
    {
        super.init (simulator);
        if (populations != null) for (Population p : populations) p.init (simulator);
    }

    public void integrate (Euler simulator)
    {
        super.integrate (simulator);
        if (populations != null) for (Population p : populations) p.integrate (simulator);
    }

    public void prepare ()
    {
        super.prepare ();
        if (populations != null) for (Population p : populations) p.prepare ();
    }

    public void update (Euler simulator)
    {
        super.update (simulator);
        if (populations != null) for (Population p : populations) p.update (simulator);
    }

    public boolean finish (Euler simulator)
    {
        if (populations != null) for (Population p : populations) p.finish (simulator);
        return super.finish (simulator);
    }
}
