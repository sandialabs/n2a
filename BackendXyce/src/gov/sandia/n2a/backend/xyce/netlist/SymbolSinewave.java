/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;

import java.util.ArrayList;

public class SymbolSinewave extends SymbolInput 
{
    public SymbolSinewave (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        ArrayList<String> params = new ArrayList<String> (5);
        for (int a = 0; a < funcNode.operands.length; a++)
        {
            params.add (renderer.change (funcNode.operands[a]));
        }

        return Xyceisms.voltageSinWave (eq.variable.name, renderer.pi.hashCode (), params);
    }
}
