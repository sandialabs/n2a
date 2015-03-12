/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;

public class Pulse extends Function
{
    public Pulse ()
    {
        name          = "pulse";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args) throws EvaluationException
    {
        if (args.length < 2) throw new EvaluationException ("pulse() requires at least two arguments");
        double t      = ((Number) args[0]).doubleValue ();
        double width  = ((Number) args[1]).doubleValue ();
        double period = 0;
        double rise   = 0;
        double fall   = 0;
        if (args.length >= 3) period = ((Number) args[2]).doubleValue ();
        if (args.length >= 4) rise   = ((Number) args[3]).doubleValue ();
        if (args.length >= 5) fall   = ((Number) args[4]).doubleValue ();

        if (period == 0.0)
        {
            if (t < 0) return 0.0;
        }
        else t %= period;
        if (t < rise) return t / rise;
        t -= rise;
        if (t < width) return 1.0;
        t -= width;
        if (t < fall) return 1.0 - t / fall;
        return 0.0;
    }
}
