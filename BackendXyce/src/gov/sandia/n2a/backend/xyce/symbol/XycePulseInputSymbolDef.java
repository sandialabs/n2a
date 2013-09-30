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

public class XycePulseInputSymbolDef extends InputSymbolDef
{
    public XycePulseInputSymbolDef(EquationEntry eq, PartSetInterface partSet) {
        super(eq, partSet);
    }

    @Override
     public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        StringBuilder result = new StringBuilder();
        int SN = pi.serialNumber;
        ArrayList<String> params = new ArrayList<String>(7);
        for(int a = 0; a < funcNode.getCount(); a++) {
            Object param = funcNode.getChild(a).getValue();
            if (param instanceof Number) {
                params.add(param.toString());
            } else {
                // param is an expression; need to get the entire string at this subtree and translate it
                params.add(XyceASTUtil.getReadableShort(funcNode.getChild(a),
                        new XyceRHSTranslator(symMgr, pi, null, false)));
            }
        }
        result.append(Xyceisms.voltagePulse(inputVar, SN, params));
        return result.toString();
    }
}
