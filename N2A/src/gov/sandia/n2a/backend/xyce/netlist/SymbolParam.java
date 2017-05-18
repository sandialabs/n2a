/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;

public class SymbolParam extends Symbol 
{
    Number value;

    public SymbolParam (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        return Xyceisms.param (eq.variable.name, renderer.pi.hashCode (), renderer.change (eq.expression));
    }

    @Override
    public String getReference (XyceRenderer renderer)
    {
        return Xyceisms.referenceVariable (eq.variable.name, renderer.pi.hashCode ());
    }
}
