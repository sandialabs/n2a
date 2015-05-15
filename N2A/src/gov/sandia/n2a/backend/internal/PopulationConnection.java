/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.type.Instance;

public class PopulationConnection extends Population
{
    public PopulationConnection (EquationSet equations, Instance container)
    {
        super (equations, container);
    }

    /// @return The Population associated with the given position in EquationSet.connectionBindings collection
    public Population getTarget (int i)
    {
        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;
        return ((Part) container).populations[bed.connectionTargets[i]];
    }

    public void init (Euler simulator)
    {
        super.init (simulator);
        simulator.connect (this);  // queue to evaluate our connections
    }

    public void connect (Euler simulator)
    {
        
    }
}
