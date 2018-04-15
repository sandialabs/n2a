/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.type.Instance;

public class Power extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "^";
            }

            public Operator createInstance ()
            {
                return new Power ();
            }
        };
    }

    public Associativity associativity ()
    {
        return Associativity.RIGHT_TO_LEFT;  // TODO: need to implement this in the parser
    }

    public int precedence ()
    {
        return 3;
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).power (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs, boolean right)
    {
        if (right)
        {
            Divide inv = new Divide ();
            inv.operand0 = new Constant (1);
            inv.operand1 = lhs;
            Power result = new Power ();
            result.operand0 = rhs;
            result.operand1 = inv;
            return result;
        }

        Log log = new Log ();
        log.operands[0] = lhs;
        Divide result = new Divide ();
        result.operand0 = rhs;
        result.operand1 = log;
        return result;
    }

    public String toString ()
    {
        return "^";
    }
}
