/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;
import gov.sandia.n2a.language.type.Matrix;

public class Exp extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "exp";
            }

            public Operator createInstance ()
            {
                return new Exp ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        Operator op = operands[0];
        op.determineExponent (context);

        // If op is unsigned, we can solve for center as follows:
        // o = power of op.center
        // m = o^2 = magnitude of op.center
        // p = power of our center (the effective output of this function)
        //   = floor(log2(exp(m))) = floor(ln(exp(m)) / ln(2)) = floor(m / ln(2));

        // Currently, op is always signed. The best we can do is assume output is centered around 1.
        int centerNew   = MSB / 2;
        int exponentNew = 0;
        if (operands.length >= 2) exponentNew = getExponentHint (operands[1].getString (), exponentNew);
        exponentNew += MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        Operator op = operands[0];
        // exp(n) rapidly explodes, so no benefit in allowing arbitrary magnitude. Instead, use those bits for precision.
        // Our goal with fixed-point is to roughly match the performance of single-precision float.
        // The largest number representable in float is 2^127, so allocating 8 bits above the decimal
        // should be more than enough.
        op.exponentNext = 7;
        op.determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.exp (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.exp (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public void solve (Equality statement) throws EvaluationException
    {
        statement.lhs = operands[0];
        Log log = new Log ();
        log.operands = new Operator[1];
        log.operands[0] = statement.rhs;
        statement.rhs = log;
    }

    public String toString ()
    {
        return "exp";
    }
}
