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
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;

import java.util.ArrayList;

public class SineWaveInputSymbolDef extends InputSymbolDef 
{
    public SineWaveInputSymbolDef(EquationEntry eq, PartSetInterface partSet) {
        super(eq, partSet);
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        int SN = pi.serialNumber;
        ArrayList<String> params = new ArrayList<String> (5);
        for (int a = 0; a < funcNode.operands.length; a++)
        {
            Operator param = funcNode.operands[a];
            if (param instanceof Constant)
            {
                params.add (param.toString ());
            }
            else
            {
                // param is an expression; need to get the entire string at this subtree and translate it
                params.add(XyceASTUtil.getReadableShort(param,
                        new XyceRHSTranslator(symMgr, pi, null, false)));
            }
        }

        StringBuilder result = new StringBuilder ();
        result.append(Xyceisms.voltageSinWave(inputVar, SN, params));
        return result.toString();
    }
}