/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.parsing.XyceRenderer;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.Function;

public abstract class InputSymbolDef extends SymbolDef
{
    Function funcNode;

    public InputSymbolDef (EquationEntry eq) 
    {
        super (eq);
        funcNode = (Function) eq.expression;
    }

    @Override
    public String getReference (XyceRenderer renderer)
    {
        return Xyceisms.referenceStateVar (eq.variable.name, renderer.pi.hashCode ());
    }
}
