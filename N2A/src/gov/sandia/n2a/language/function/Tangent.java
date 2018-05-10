/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class Tangent extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "tan";
            }

            public Operator createInstance ()
            {
                return new Tangent ();
            }
        };
    }

    public void determineExponent (Variable from)
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponent (from);

        int exponentNew = MSB / 2 - 1;  // Q16.16
        if (operands.length >= 2) exponentNew = getExponentHint (operands[1].getString (), exponentNew);
        updateExponent (from, exponentNew);
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.tan (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.tan (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "tan";
    }
}
