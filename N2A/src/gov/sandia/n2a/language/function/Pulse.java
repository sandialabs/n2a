/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Pulse extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "pulse";
            }

            public Operator createInstance ()
            {
                return new Pulse ();
            }
        };
    }

    public Type eval (Instance context)
    {
        double t      = ((Scalar) operands[0].eval (context)).value;
        double width  = ((Scalar) operands[1].eval (context)).value;
        double period = 0;
        double rise   = 0;
        double fall   = 0;
        if (operands.length >= 3) period = ((Scalar) operands[2].eval (context)).value;
        if (operands.length >= 4) rise   = ((Scalar) operands[3].eval (context)).value;
        if (operands.length >= 5) fall   = ((Scalar) operands[4].eval (context)).value;

        if (period == 0.0)
        {
            if (t < 0) return new Scalar (0);
        }
        else t %= period;
        if (t < rise) return new Scalar (t / rise);
        t -= rise;
        if (t < width) return new Scalar (1);
        t -= width;
        if (t < fall) return new Scalar (1.0 - t / fall);
        return new Scalar (0);
    }

    public String toString ()
    {
        return "pulse";
    }
}
