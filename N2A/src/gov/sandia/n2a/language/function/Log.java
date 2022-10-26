/*
Copyright 2017-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
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

public class Log extends Function implements MatrixVisitable
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "log";
            }

            public Operator createInstance ()
            {
                return new Log ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        Operator op = operands[0];
        op.determineExponent (context);

        // let o = power of center of operand
        // let p = power of center of result
        // p = log2(log(2^o)) = log2(o*log(2)) = log2(o)+log2(log(2)) = log2(o) - constant
        // If o is negative, negate the result with abs(o).

        if (op.exponent == UNKNOWN) return;
        int o = op.centerPower ();
        int p = 0;
        if (o != 0) p = (int) (Math.signum (o) * Math.round (Math.log (Math.abs (o)) / Math.log (2)));
        int centerNew   = MSB / 2;
        int exponentNew = p + MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);
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

    public boolean hasExponentResult ()
    {
        return true;
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.log (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.log (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public void solve (Equality statement) throws EvaluationException
    {
        statement.lhs = operands[0];
        Exp exp = new Exp ();
        exp.operands = new Operator[1];
        exp.operands[0] = statement.rhs;
        statement.rhs = exp;
    }

    public String toString ()
    {
        return "log";
    }
}
