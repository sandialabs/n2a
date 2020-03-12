/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class Negate extends OperatorUnary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "UM";
            }

            public Operator createInstance ()
            {
                return new Negate ();
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
        return operand.eval (context).negate ();
    }

    public double getDouble ()
    {
        return - operand.getDouble ();
    }

    public boolean isScalar ()
    {
        return operand.isScalar ();
    }

    public void solve (Equality statement) throws EvaluationException
    {
        statement.lhs = operand;
        operand = statement.rhs;
        statement.rhs = this;
    }

    public String toString ()
    {
        return "-";
    }
}
