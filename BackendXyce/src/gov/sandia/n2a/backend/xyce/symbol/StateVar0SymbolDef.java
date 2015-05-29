/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.XyceBackendData;
import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRenderer;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.type.Instance;

import java.util.ArrayList;

public class StateVar0SymbolDef extends DefaultSymbolDef
{
    // For state variables defined by an order 0 equation - 
    // no time derivative
    // Note - this class intentionally does not handle cases 
    // where the variable belongs to a connected part rather than
    // this one

    public StateVar0SymbolDef (EquationEntry eq)
    {
        super(eq);
    }

    @Override
    public String getDefinition (Instance pi)
    {
        XyceBackendData bed = (XyceBackendData) eq.variable.container.backendData;
        String translatedEq = XyceASTUtil.getRightHandSideReadableShort (eq, new XyceRenderer (bed, pi, new ArrayList<String> (), false));
        return Xyceisms.defineStateVar (eq.variable.name, pi.hashCode (), translatedEq);
    }

    @Override
    public String getReference (Instance pi)
    {
        return Xyceisms.referenceStateVar (eq.variable.name, pi.hashCode ());
    }
}
