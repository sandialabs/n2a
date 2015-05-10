/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.Type;

public class NOT extends OperatorUnary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "!";
            }

            public Operator createInstance ()
            {
                return new NOT ();
            }
        };
    }

    public Associativity associativity ()
    {
        return Associativity.RIGHT_TO_LEFT;
    }

    public int precedence ()
    {
        return 2;
    }

    public Type eval (EvaluationContext context)
    {
        return operand.eval (context).NOT ();
    }

    public String toString ()
    {
        return "!";
    }
}
