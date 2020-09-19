/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class HyperbolicTangent extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "tanh";
            }

            public Operator createInstance ()
            {
                return new HyperbolicTangent ();
            }
        };
    }

    public void determineExponent (Variable from)
    {
        operands[0].determineExponent (from);
        updateExponent (from, 0, MSB - 1);  // result is always in [-1,1]. TODO: select center more carefully, based on center of operand
    }

    public void determineExponentNext (Variable from)
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponentNext (from);
    }

    // Use Function.determineUnit()
    // Since tanh() is often used as a squashing function, it makes sense to treat its output
    // as having the same units as its input.

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.tanh (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.tanh (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "tanh";
    }
}
