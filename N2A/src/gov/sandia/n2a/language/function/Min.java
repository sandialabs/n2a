/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

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

    public Type eval (Instance context)
    {
        Type arg0 = operands[0].eval (context);
        Type arg1 = operands[1].eval (context);
        return arg0.min (arg1);
    }

    public String toString ()
    {
        return "min";
    }
}
