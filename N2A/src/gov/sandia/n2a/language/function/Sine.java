/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.MatrixVisitable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;
import gov.sandia.n2a.language.type.Matrix;

public class Sine extends Function implements MatrixVisitable
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "sin";
            }

            public Operator createInstance ()
            {
                return new Sine ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        operands[0].determineExponent (context);
        updateExponent (context, 1 - MSB, MSB - 2);  // Largest absolute value is 1, but we allow one extra bit above the decimal for the convenience of the C implementation.
    }

    public void determineExponentNext ()
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponentNext ();
    }

    public boolean hasExponentA ()
    {
        return true;
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operands[0].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.sin (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.sin (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "sin";
    }
}
