/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

public class AssignAdd extends Add
{
    public AssignAdd ()
    {
        name          = "+=";
        associativity = Associativity.RIGHT_TO_LEFT;
        precedence    = 12;
        assignment    = true;
    }

    public Object eval (Object[] args)
    {
        return ((Number) args[0]).doubleValue () + ((Number) args[1]).doubleValue ();
    }
}
