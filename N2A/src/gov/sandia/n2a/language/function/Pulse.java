/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;

public class Pulse extends Operator
{
    public Pulse ()
    {
        name          = "pulse";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Type eval (Type[] args) throws EvaluationException
    {
        if (args.length < 2) throw new EvaluationException ("pulse() requires at least two arguments");
        double t      = ((Scalar) args[0]).value;
        double width  = ((Scalar) args[1]).value;
        double period = 0;
        double rise   = 0;
        double fall   = 0;
        if (args.length >= 3) period = ((Scalar) args[2]).value;
        if (args.length >= 4) rise   = ((Scalar) args[3]).value;
        if (args.length >= 5) fall   = ((Scalar) args[4]).value;

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
}
