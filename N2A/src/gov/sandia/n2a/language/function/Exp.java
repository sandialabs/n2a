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

        // let o = power of op.center
        // let m = o^2 = magnitude of op.center
        // let p = power of our center (the effective output of this function)
        // for op >= 0:
        //     p = floor (log2 (e^m)) = floor (ln (e^m) / ln (2)) = floor (m / ln (2));
        // for op < 0:
        //     User must specify magnitude, since we don't try to predict sign.

        int exponentNew = Integer.MIN_VALUE;
        if (op.exponent != Integer.MIN_VALUE)
        {
            int o = op.centerPower ();
            double m = Math.pow (2, o);
            int pow = (int) Math.floor (m / Math.log (2));  // power of our output center
            exponentNew = pow + MSB / 2 - 1;
        }
        if (operands.length >= 2) exponentNew = getExponentHint (operands[1].getString (), exponentNew);
        updateExponent (from, exponentNew, MSB / 2 + 1);  // typically Q16.16
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
