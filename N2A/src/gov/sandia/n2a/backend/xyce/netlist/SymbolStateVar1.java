/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.type.Instance;

/// For state variables defined by a first-order differential equation
public class SymbolStateVar1 extends Symbol 
{
    public SymbolStateVar1 (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        String translatedEq = renderer.change (eq.expression);

        Variable          v = eq.variable;
        VariableReference r = v.reference;
        if (r.index < 0)  // symbol is defined here; no += allowed within same part
        {
            return Xyceisms.defineDiffEq (v.name, renderer.pi.hashCode (), translatedEq);
        }

        // This symbol refers to a symbol in another part. We don't re-define the
        // variable, rather we create another diff eq that updates the existing one.
        Instance target = (Instance) renderer.pi.valuesObject[r.index];
        String thisVarname = r.variable.name + "_" + target     .hashCode ();
        String eqName      = v.name          + "_" + renderer.pi.hashCode ();
        return Xyceisms.updateDiffEq (eqName, thisVarname, translatedEq);
    }
}
