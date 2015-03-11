/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Function;

public class LE extends Function
{
    public LE ()
    {
        name          = "<=";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 6;
    }

    public Object eval (Object[] args)
    {
        double arg0 = ((Number) args[0]).doubleValue ();
        double arg1 = ((Number) args[1]).doubleValue ();
        return (arg0 <= arg1) ? 1.0 : 0.0;
    }
}
