/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.operator.Multiply;

public class Draw3D extends Draw
{
    public Operator simplify (Variable from, boolean evalOnly)
    {
        if (operands.length < 2) return super.simplify (from, evalOnly);

        // "center" is present, so fold it into model matrix.
        Operator center = operands[1];
        glTranslate t = new glTranslate ();
        t.operands = new Operator[1];
        t.operands[0] = center;
        center.parent = t;

        Operator model = getKeyword ("model");
        if (model == null)
        {
            addKeyword ("model", t);
        }
        else
        {
            Multiply m = new Multiply ();
            m.parent = this;
            m.operand0 = t;
            m.operand1 = model;
            t.parent = m;
            model.parent = m;
            keywords.put ("model", m);
        }
        Operator[] nextOperands = new Operator[1];
        nextOperands[0] = operands[0];
        operands = nextOperands;

        from.changed = true;
        return this;
    }

    public boolean needModelMatrix ()
    {
        return true;
    }
}
