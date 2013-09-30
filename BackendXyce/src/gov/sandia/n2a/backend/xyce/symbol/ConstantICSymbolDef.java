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
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.parsing.functions.EvaluationContext;

public class ConstantICSymbolDef extends DefaultSymbolDef {

    private Number value;

    public ConstantICSymbolDef(EquationEntry eq, Number firstValue, PartInstance firstPI)
    {
        super(eq, firstPI);
        this.value = firstValue;
    }

    @Override
    public String getReference(int SN) 
    {
        if (instanceSpecific) {
            return Xyceisms.referenceVariable(name, SN);
        }
        return Xyceisms.referenceVariable(name, firstInstance.serialNumber);
    }

    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        if (defWritten) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        if (instanceSpecific) {
            EvaluationContext context = XyceASTUtil.getInstanceContext(eq, pi, true);
            Object evalResult = XyceASTUtil.evaluateEq(eq, context);
            if (!(evalResult instanceof Number)) {
                throw new RuntimeException("unexpected evaluation result for " + eq.toString());
            }
            value = (Number)evalResult;
            result.append(Xyceisms.setInitialCondition(name, pi.serialNumber, value));
        } else {
            result.append(Xyceisms.setInitialCondition(name, firstInstance.serialNumber, value));
            defWritten = true;
        }
        return result.toString();
    }
}
