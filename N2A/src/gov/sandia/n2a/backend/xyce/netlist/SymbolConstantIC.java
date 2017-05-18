/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;

public class SymbolConstantIC extends Symbol
{
    public SymbolConstantIC (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getReference (XyceRenderer renderer) 
    {
        return Xyceisms.referenceVariable (eq.variable.name, renderer.pi.hashCode ());
    }

    public String getDefinition (XyceRenderer renderer)
    {
        // Note: this is done for every instance, so they can have different initial values for the same variable.
        Type stored = renderer.pi.get (eq.variable);
        if (! (stored instanceof Scalar)) throw new EvaluationException ("unexpected evaluation result for " + eq.toString ());
        return Xyceisms.setInitialCondition (eq.variable.name, renderer.pi.hashCode (), ((Scalar) stored).value);
    }
}
