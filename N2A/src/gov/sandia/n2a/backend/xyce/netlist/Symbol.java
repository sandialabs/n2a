/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;

public class Symbol 
{
    public EquationEntry eq;

    public Symbol (EquationEntry eq)
    {
        this.eq = eq;
    }

    public String getDefinition (XyceRenderer renderer)
    {
        return "";
    }

    /**
        Output code necessary to retrieve the value of this symbol.
        Note that this function is not called until external references are resolved
        (via recursion in XyceRenderer), so no need to worry about resolving them here.
        Default action is to access the voltage of a node. Specific symbol types may
        override this.
    **/
    public String getReference (XyceRenderer renderer)
    {
        return Xyceisms.referenceStateVar (eq.variable.name, renderer.pi.hashCode ());
    }
}
