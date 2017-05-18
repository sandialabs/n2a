/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.function;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Sinewave extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "sinewave";
            }

            public Operator createInstance ()
            {
                return new Sinewave ();
            }
        };
    }

    public Type eval (Instance context)
    {
        // TODO: implement equivalent of Xyce sinewave function; alternately, define a generic sinewave function in N2A, similar to generic pulse()
        return new Scalar (0);
    }

    public String toString ()
    {
        return "sinewave";
    }
}
