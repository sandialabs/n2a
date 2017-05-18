/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;

public class SymbolStateVar0 extends Symbol
{
    // For state variables defined by an order 0 equation - 
    // no time derivative
    // Note - this class intentionally does not handle cases 
    // where the variable belongs to a connected part rather than
    // this one

    public SymbolStateVar0 (EquationEntry eq)
    {
        super(eq);
    }

    @Override
    public String getDefinition (XyceRenderer renderer)
    {
        return Xyceisms.defineStateVar (eq.variable.name, renderer.pi.hashCode (), renderer.change (eq.expression));
    }
}
