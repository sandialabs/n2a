/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.eqset.EquationEntry;

import java.util.ArrayList;

public class StateVar0SymbolDef extends DefaultSymbolDef
{
    // For state variables defined by an order 0 equation - 
    // no time derivative
    // Note - this class intentionally does not handle cases 
    // where the variable belongs to a connected part rather than
    // this one

    public StateVar0SymbolDef(EquationEntry eq, PartSetInterface partSet)
    {
        super(eq, partSet);
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) {
        StringBuilder result = new StringBuilder();
        String translatedEq = XyceASTUtil.getRightHandSideReadableShort(eq,
            new XyceRHSTranslator(symMgr, pi, new ArrayList<String>(), false));
        result.append(Xyceisms.defineStateVar(eq.variable.name, pi.serialNumber, translatedEq));
        return result.toString();
    }

    @Override
    public String getReference(int SN) {
        return Xyceisms.referenceStateVar(eq.variable.name, SN);
    }
}
