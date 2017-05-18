/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;

public class Compartment extends Part
{
    public Compartment before;
    public Compartment after;

    public Compartment (EquationSet equations, PopulationCompartment container)
    {
        super (equations, container);
    }

    public void die ()
    {
        super.die ();
        ((PopulationCompartment) container).remove (this);
    }

    public Part convert (EquationSet other)
    {
        InternalBackendData otherBed = (InternalBackendData) equations.backendData;
        PopulationCompartment otherPopulation = (PopulationCompartment) ((Part) container.container).populations[otherBed.populationIndex];
        Compartment result = new Compartment (other, otherPopulation);
        otherPopulation.insert (result);
        return result;
    }
}
