/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.Function;

public class Lognormal extends Function
{
    public Lognormal ()
    {
        name          = "lognormal";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args)
    {
        double location = ((Number) args[0]).doubleValue ();
        double scale    = ((Number) args[1]).doubleValue ();
        double r = Math.sqrt (-2 * Math.log (Math.random ()));
        double theta = 2 * Math.PI * Math.random ();
        return Math.exp (location + (scale * r * Math.cos (theta)));
    }

}
