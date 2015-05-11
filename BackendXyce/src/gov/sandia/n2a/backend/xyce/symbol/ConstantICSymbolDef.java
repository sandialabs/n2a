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
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class ConstantICSymbolDef extends DefaultSymbolDef {

    private Scalar value;

    public ConstantICSymbolDef(EquationEntry eq, Scalar firstValue, PartInstance firstPI)
    {
        super(eq, firstPI);
        value = firstValue;
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
            Instance context = XyceASTUtil.getInstanceContext(eq, pi, true);
            Type evalResult = eq.expression.eval (context);
            if (!(evalResult instanceof Scalar)) {
                throw new RuntimeException("unexpected evaluation result for " + eq.toString());
            }
            value = (Scalar) evalResult;
            result.append(Xyceisms.setInitialCondition(name, pi.serialNumber, value.value));
        } else {
            result.append(Xyceisms.setInitialCondition(name, firstInstance.serialNumber, value.value));
            defWritten = true;
        }
        return result.toString();
    }
}
