/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.OperatorLogical;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import tech.units.indriya.AbstractUnit;

public class OR extends OperatorBinary implements OperatorLogical
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "||";
            }

            public Operator createInstance ()
            {
                return new OR ();
            }
        };
    }

    public int precedence ()
    {
        return 10;  // See comment on AND.precedence()
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        if (operand0.isScalar ())
        {
            from.changed = true;
            result = operand1;
            if (operand0.getDouble () != 0)
            {
                if (! evalOnly) operand1.releaseDependencies (from);
                result = new Constant (1);
            }
            result.parent = parent;
            return result;
        }
        else if (operand1.isScalar ())
        {
            from.changed = true;
            result = operand0;
            if (operand1.getDouble () != 0)
            {
                if (! evalOnly) operand0.releaseDependencies (from);
                result = new Constant (1);
            }
            result.parent = parent;
            return result;
        }
        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        operand0.determineExponent (context);
        operand1.determineExponent (context);
        int centerNew   = MSB / 2;
        int exponentNew = MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operand0.determineUnit (fatal);
        operand1.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).OR (operand1.eval (context));
    }

    public String toString ()
    {
        return "||";
    }
}
