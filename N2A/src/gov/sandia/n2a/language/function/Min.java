/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;

public class Min extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "min";
            }

            public Operator createInstance ()
            {
                return new Min ();
            }
        };
    }

    public Type eval (EvaluationContext context)
    {
        Type arg0 = operands[0].eval (context);
        Type arg1 = operands[1].eval (context);
        if (arg0 instanceof Scalar  &&  arg1 instanceof Scalar)
        {
            return new Scalar (Math.min (((Scalar) arg0).value, ((Scalar) arg1).value));
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "min";
    }
}
