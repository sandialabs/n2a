/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.eqset.EquationEntry;

import java.util.ArrayList;

public class ParamSymbolDef extends DefaultSymbolDef 
{
    Number value;

    public ParamSymbolDef(EquationEntry eq, PartInstance firstPI)
    {
        super(eq, firstPI);
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        if (defWritten) {
            return "";
        }
        if (instanceSpecific) {
            String translatedEq = XyceASTUtil.getRightHandSideReadableShort(eq,
                new XyceRHSTranslator(symMgr, pi, new ArrayList<String>(), false));
            return Xyceisms.param(name, pi.serialNumber, translatedEq);
        } else {
            String translatedEq = XyceASTUtil.getRightHandSideReadableShort(eq,
                    new XyceRHSTranslator(symMgr, firstInstance, new ArrayList<String>(), false));
            defWritten = true;
            return Xyceisms.param(name, firstInstance.serialNumber, translatedEq);
        }
    }

    @Override
    public String getReference(int SN) {
        if (instanceSpecific) {
            return Xyceisms.referenceVariable(name, SN);
        }
        return Xyceisms.referenceVariable(name, firstInstance.serialNumber);
    }
}
