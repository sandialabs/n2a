/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Floor extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "floor";
            }

            public Operator createInstance ()
            {
                return new Floor ();
            }
        };
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.floor (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit (new Matrix.Visitor ()
            {
                public double apply (double a)
                {
                    return Math.floor (a);
                }
            });
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "floor";
    }
}
