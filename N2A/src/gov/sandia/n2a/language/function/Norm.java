/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Norm extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "norm";
            }

            public Operator createInstance ()
            {
                return new Norm ();
            }
        };
    }

    public void determineExponent (Variable from)
    {
        for (int i = 0; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = op.exponent;
            op.determineExponent (from);
        }

        Operator op0 = operands[0];  // n
        Operator op1 = operands[1];  // A

        Instance instance = new Instance ()
        {
            // all AccessVariable objects will reach here first, and get back the Variable.type field
            public Type get (VariableReference r) throws EvaluationException
            {
                return r.variable.type;
            }
        };
        Matrix A = (Matrix) op1.eval (instance);
        int Asize = A.rows () * A.columns ();

        int centerNext   = center;
        int exponentNext = exponent;
        if (op1.exponent != Integer.MIN_VALUE)
        {
            // For n==1 (sum of elements), which is the most expensive in terms of bits.
            int shift = (int) Math.floor (Math.log (Asize) / Math.log (2));
            centerNext   = op1.center   + shift;
            exponentNext = op1.exponent + shift;
        }
        if (op0 instanceof Constant)
        {
            double n = ((Scalar) ((Constant) op0).value).value;
            if (n == 0)
            {
                // Result is an integer
                centerNext   = 0;
                exponentNext = MSB;
            }
            else if (Double.isInfinite (n))
            {
                centerNext   = op1.center;
                exponentNext = op1.exponent;
            }
            // It would be nice to have some way to interpolate between the 3 bounding cases.
        }

        updateExponent (from, exponentNext, centerNext);
    }

    public Type eval (Instance context)
    {
        double n = ((Scalar) operands[0].eval (context)).value;
        Matrix A =  (Matrix) operands[1].eval (context);
        return new Scalar (A.norm (n));
    }

    public String toString ()
    {
        return "norm";
    }
}
