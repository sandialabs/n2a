/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.Function;

public class Power extends Function
{
    public Power ()
    {
        name          = "^";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 3;
    }

    public Object eval (Object[] args)
    {
        return Math.pow (((Number) args[0]).doubleValue (), ((Number) args[1]).doubleValue ());
    }
}
