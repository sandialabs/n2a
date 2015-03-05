/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.gen.ASTFunNode;

public abstract class InputSymbolDef extends DefaultSymbolDef
{
    ASTFunNode funcNode;
    String inputVar;

    public InputSymbolDef(EquationEntry eq, PartSetInterface pSet) 
    {
        super(eq,pSet);
        this.inputVar = eq.variable.name;
        funcNode = (ASTFunNode) eq.expression;
    }

    @Override
    public String getReference(int SN) 
    {
        return Xyceisms.referenceStateVar(inputVar, SN);
    }
}
