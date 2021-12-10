/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Comparison;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class LT extends Comparison
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "<";
            }

            public Operator createInstance ()
            {
                return new LT ();
            }
        };
    }

    public int precedence ()
    {
        return 6;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        double a = operand0.getDouble ();
        if (Double.isInfinite (a)  &&  a > 0)  // positive infinity can never be less than anything
        {
            from.changed = true;
            result = new Constant (0);
            result.parent = parent;
            return result;
        }

        double b = operand1.getDouble ();
        if (Double.isInfinite (b)  &&  b < 0)  // nothing can be less than negative infinity
        {
            from.changed = true;
            result = new Constant (0);
            result.parent = parent;
            return result;
        }

        return this;
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).LT (operand1.eval (context));
    }

    public String toString ()
    {
        return "<";
    }
}
