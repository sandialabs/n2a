/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class Log extends Function
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

    public void determineExponent (Variable from)
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponent (from);

        // let o = power of center of operand
        // let p = power of center of result
        // p = log2(log(2^o)) = log2(o*log(2)) = log2(o)+log2(log(2)) < log2(o)+1

        if (op.exponent == UNKNOWN) return;
        int o = op.centerPower ();
        int p = (int) Math.floor (Math.log (o) / Math.log (2)) + 1;
        int centerNew   = MSB / 2;
        int exponentNew = p + MSB - centerNew;
        updateExponent (from, exponentNew, centerNew);
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
