/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
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

    public Operator simplify (Variable from, boolean evalOnly)
    {
        if (operands.length == 1)
        {
            Operator[] nextOperands = new Operator[2];
            nextOperands[0] = operands[0];
            nextOperands[1] = new Constant (2);
            operands = nextOperands;
        }

        return super.simplify (from, evalOnly);
    }

    public void determineExponent (ExponentContext context)
    {
        Operator op0 = operands[0];  // A
        op0.determineExponent (context);

        Operator op1 = operands[1];  // n
        op1.determineExponent (context);

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

        int centerNew   = center;
        int exponentNew = exponent;
        if (op0.exponent != UNKNOWN)
        {
            // For n==1 (sum of elements), which is the most expensive in terms of bits.
            int shift = (int) Math.floor (Math.log (Asize) / Math.log (2));
            shift = Math.min (op0.center, shift);  // Don't shift center into negative territory.
            centerNew   = op0.center   - shift;
            exponentNew = op0.exponent + shift;
        }
        if (op1 instanceof Constant)
        {
            double n = op1.getDouble ();
            if (n == 0)
            {
                // Result is an integer
                centerNew   = 0;
                exponentNew = MSB;
            }
            else if (Double.isInfinite (n))
            {
                centerNew   = op0.center;
                exponentNew = op0.exponent;
            }
            // It would be nice to have some way to interpolate between the 3 bounding cases.
        }

        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        Operator op0 = operands[0];  // A
        op0.exponentNext = op0.exponent;
        op0.determineExponentNext ();

        Operator op1 = operands[1];  // n
        op1.exponentNext = Operator.MSB / 2;  // required by fixedpoint in C backend
        op1.determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        Operator op0 = operands[0];  // A
        op0.determineUnit (fatal);
        unit = op0.unit;
        if (operands.length > 1) operands[1].determineUnit (fatal);  // n
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Matrix A = (Matrix) operands[0].eval (context);
        double n;
        if (operands.length > 1) n = ((Scalar) operands[1].eval (context)).value;
        else                     n = 2;
        return new Scalar (A.norm (n));
    }

    public String toString ()
    {
        return "norm";
    }
}
