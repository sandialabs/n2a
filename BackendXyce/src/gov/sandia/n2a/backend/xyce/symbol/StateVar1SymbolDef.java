/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.internal.Connection;
import gov.sandia.n2a.backend.xyce.XyceBackendData;
import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRenderer;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.type.Instance;

import java.util.ArrayList;

/// For state variables defined by a first-order differential equation
public class StateVar1SymbolDef extends DefaultSymbolDef 
{
    public StateVar1SymbolDef (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getReference (Instance pi)
    {
        return Xyceisms.referenceStateVar (eq.variable.name, pi.hashCode ());
    }

    @Override
    public String getDefinition (Instance pi) 
    {
        XyceBackendData bed = (XyceBackendData) eq.variable.container.backendData;
        String translatedEq = XyceASTUtil.getRightHandSideReadableShort (eq, new XyceRenderer (bed, pi, new ArrayList<String> (), false));

        // If this symbol refers to a symbol in another part, we don't re-define the
        // variable, we create another diff eq that updates the existing one.
        Variable          v = eq.variable;
        VariableReference r = v.reference;
        if (! (pi instanceof Connection)  ||  v == r.variable)  // symbol is defined here; no += allowed within same part
        {
            return Xyceisms.defineDiffEq (v.name, pi.hashCode (), translatedEq);
        }

        Instance target = (Instance) pi.valuesType[r.index];
        String thisVarname = r.variable.name + "_" + target.hashCode ();
        String eqName      = v.name          + "_" + pi    .hashCode ();
        return Xyceisms.updateDiffEq (eqName, thisVarname, translatedEq);
    }
}
