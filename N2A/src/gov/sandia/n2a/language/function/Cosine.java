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
import tec.uom.se.AbstractUnit;
import gov.sandia.n2a.language.type.Matrix;

public class Cosine extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "cos";
            }

            public Operator createInstance ()
            {
                return new Cosine ();
            }
        };
    }

    public void determineExponent (Variable from)
    {
        operands[0].determineExponent (from);
        updateExponent (from, 1, MSB - 2);  // Largest absolute value is 1, but we allow one extra bit above the decimal for the convenience of the C implementation.
    }

    public void determineExponentNext (Variable from)
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponentNext (from);
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operands[0].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.cos (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.cos (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "cos";
    }
}
