/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;

public class Negate extends Function
{
    public Negate ()
    {
        name          = "UM";  // as in "unary minus"
        associativity = Associativity.RIGHT_TO_LEFT;
        precedence    = 2;
    }

    public Object eval (Object[] args) throws EvaluationException
    {
        return ((Number) args[0]).doubleValue () * -1;
    }

    public String toString ()
    {
        return "-";
    }
}
