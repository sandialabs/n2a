/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Round extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "round";
            }

            public Operator createInstance ()
            {
                return new Round ();
            }
        };
    }

    public void determineExponent (Variable from)
    {
        Operator op = operands[0];
        if (op.exponent < 0)        op.exponentNext = 0;  // Must have at least one bit above the decimal point in order to round.
        else if (op.exponent < MSB) op.exponentNext = op.exponent;  // Decimal point is visible, so we can process this.
        else                        op.exponentNext = exponentNext; // Otherwise, just pass through.
        op.determineExponent (from);
        updateExponent (from, op.exponent, op.center);
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.round (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit (new Matrix.Visitor ()
            {
                public double apply (double a)
                {
                    return Math.round (a);
                }
            });
        }
        throw new EvaluationException ("type mismatch");
    }

    public void solve (Equality statement) throws EvaluationException
    {
        // Pretend that the round() operation does not change the value much,
        // so simply strip it off.
        statement.lhs = operands[0];
    }

    public String toString ()
    {
        return "round";
    }
}
