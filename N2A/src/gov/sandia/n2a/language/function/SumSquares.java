/*
Copyright 2021-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class SumSquares extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "sumSquares";
            }

            public Operator createInstance ()
            {
                return new SumSquares ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        // This function is related to both Norm and Power. See those classes for similar processing.

        Operator op0 = operands[0];  // A
        op0.determineExponent (context);

        // Determine number of elements in A
        Instance instance = new Instance ()
        {
            // all AccessVariable objects will reach here first, and get back the Variable.type field
            public Type get (VariableReference r) throws EvaluationException
            {
                return r.variable.type;
            }
        };
        Matrix A = (Matrix) op0.eval (instance);
        int Asize = A.rows () * A.columns ();

        if (op0.exponent != UNKNOWN)
        {
            int shift = (int) Math.floor (Math.log (Asize) / Math.log (2));   // log2(Asize)
            int centerNew   = MSB / 2;
            int exponentNew = op0.centerPower () * 2 + shift - centerNew;
            updateExponent (context, exponentNew, centerNew);
        }
    }

    public void determineExponentNext ()
    {
        Operator op0 = operands[0];  // A
        op0.exponentNext = op0.exponent;
        op0.determineExponentNext ();
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Matrix A = (Matrix) operands[0].eval (context);
        return new Scalar (A.sumSquares ());
    }

    public String toString ()
    {
        return "sumSquares";
    }
}
