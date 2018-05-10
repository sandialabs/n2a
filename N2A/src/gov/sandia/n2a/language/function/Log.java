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

        if (op.exponent == Integer.MIN_VALUE) return;
        double m = Math.pow (2, op.exponent + 1) - 1;  // magnitude of the input
        double a = Math.log (m);  // magnitude of the answer
        updateExponent (from, (int) Math.floor (Math.log (a) / Math.log (2)));
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
