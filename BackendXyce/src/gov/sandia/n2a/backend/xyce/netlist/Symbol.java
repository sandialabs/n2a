/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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

    public String getReference (XyceRenderer renderer)
    {
        return "";
    }
}
