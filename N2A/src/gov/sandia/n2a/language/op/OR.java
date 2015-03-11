/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.Function;

public class OR extends Function
{
    public OR ()
    {
        name          = "||";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 9;
    }

    public Object eval (Object[] args)
    {
        double arg0 = ((Number) args[0]).doubleValue ();
        double arg1 = ((Number) args[1]).doubleValue ();
        return (Math.abs (arg0) + Math.abs (arg1) != 0) ? 1.0 : 0.0;
    }
}
