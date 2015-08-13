/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class DollarEvent extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "$event";
            }

            public Operator createInstance ()
            {
                return new DollarEvent ();
            }
        };
    }

    public Type eval (Instance context)
    {
        return new Scalar (0); // TODO: evaluate $event()
    }

    public String toString ()
    {
        return "$event";
    }
}
