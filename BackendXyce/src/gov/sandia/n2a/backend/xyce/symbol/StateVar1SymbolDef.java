/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.ConnectionInstance;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.eqset.EquationEntry;

import java.util.ArrayList;

public class StateVar1SymbolDef extends DefaultSymbolDef 
{
    // For state variables defined by a first-order differential equation

    private boolean definedElsewhere;

    public StateVar1SymbolDef(EquationEntry eq, PartSetInterface partSet)
    {
        super(eq, partSet);
    }

    @Override
    public String getReference(int SN) {
        return Xyceisms.referenceStateVar(eq.variable.name, SN);
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        StringBuilder result = new StringBuilder();
        String translatedEq = XyceASTUtil.getRightHandSideReadableShort(eq,
            new XyceRHSTranslator(symMgr, pi, new ArrayList<String>(), false));
        // If this symbol refers to a symbol in another part, we don't re-define the
        // variable, we create another diff eq that updates the existing one.
        String varName = eq.variable.name;
        if (pi instanceof ConnectionInstance) {
            ConnectionInstance ci = (ConnectionInstance) pi;
            int thisSN = ci.serialNumber;
            if (varName.startsWith(XyceRHSTranslator.REFPRE)) {
                int preSN = ci.A.serialNumber;
                String thisVarname = varName.substring(XyceRHSTranslator.REFPRE.length()) +
                        "_" + preSN;
                String eqName = varName+"_"+thisSN;
                result.append(Xyceisms.updateDiffEq(eqName, thisVarname, translatedEq));
            } else if (varName.startsWith(XyceRHSTranslator.REFPOST)) {
                int postSN = ci.B.serialNumber;
                String thisVarname = varName.substring(XyceRHSTranslator.REFPOST.length()) +
                        "_" + postSN;
                String eqName = varName+"_"+thisSN;
                result.append(Xyceisms.updateDiffEq(eqName, thisVarname, translatedEq));
            } else {    // symbol is defined in connection
                result.append(Xyceisms.defineDiffEq(varName, pi.serialNumber, translatedEq));
            }
        } else {    // this is a compartment and symbol is defined here; no += allowed within same part
            result.append(Xyceisms.defineDiffEq(eq.variable.name, pi.serialNumber, translatedEq));
        }
        return result.toString();
    }
}
