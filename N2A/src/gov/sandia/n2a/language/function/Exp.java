/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

    public void determineExponent (Variable from)
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponent (from);

        // let o = op.exponent
        // let m = 2^(o+1)-1   (magnitude of op)
        // let p = our exponent (the effective output of this function)
        // for op >= 0:
        //     p = floor (log2 (e^m)) = floor (ln (e^m) / ln (2)) = floor (m / ln (2))
        // for op < 0:
        //     User must specify magnitude, because all we can calculate is the smallest possible output,
        //     and even that is only possible if the user gives a hint the the sign is negative.

        int exponentNew = Integer.MIN_VALUE;
        if (op.exponent != Integer.MIN_VALUE) exponentNew = (int) Math.floor ((Math.pow (2, op.exponent + 1) - 1) / Math.log (2));
        if (operands.length >= 2) exponentNew = getExponentHint (operands[1].getString (), exponentNew);
        updateExponent (from, exponentNew);
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
