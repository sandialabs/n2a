/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class Transpose extends OperatorUnary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "~";
            }

            public Operator createInstance ()
            {
                return new Transpose ();
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

    public Type eval (Instance context)
    {
        return operand.eval (context).transpose ();
    }

    public String toString ()
    {
        return "~";
    }
}
