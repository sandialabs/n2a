/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;

public class Trace extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "trace";
            }

            public Operator createInstance ()
            {
                return new Trace ();
            }
        };
    }

    public boolean isOutput ()
    {
        return true;
    }

    public Type eval (EvaluationContext context)
    {
        // TODO: implement trace() for internal simulator
        return operands[0].eval (context);
    }

    public String toString ()
    {
        return "trace";
    }
}
