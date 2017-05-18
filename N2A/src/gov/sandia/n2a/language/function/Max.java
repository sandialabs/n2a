/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class Max extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "max";
            }

            public Operator createInstance ()
            {
                return new Max ();
            }
        };
    }

    public Type eval (Instance context)
    {
        Type arg0 = operands[0].eval (context);
        Type arg1 = operands[1].eval (context);
        if (arg0 instanceof Scalar  &&  arg1 instanceof Scalar)
        {
            return new Scalar (Math.max (((Scalar) arg0).value, ((Scalar) arg1).value));
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "max";
    }
}
