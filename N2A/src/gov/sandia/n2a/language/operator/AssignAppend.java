/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Type;

public class AssignAppend extends Function
{
    public AssignAppend ()
    {
        name          = ":=";
        associativity = Associativity.RIGHT_TO_LEFT;
        precedence    = 12;
        assignment    = true;
    }

    public Type eval (Type[] args)
    {
        return args[1];
    }
}
