/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

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

    public void determineExponent (Variable from)
    {
        operand.exponentNext = operand.exponent;
        operand.determineExponent (from);
        int centerNew   = MSB / 2;
        int exponentNew = MSB - centerNew;
        updateExponent (from, exponentNew, centerNew);
    }

    public Type eval (Instance context)
    {
        return operand.eval (context).NOT ();
    }

    public String toString ()
    {
        return "!";
    }
}
