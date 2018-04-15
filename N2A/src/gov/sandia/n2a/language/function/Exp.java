/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
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
