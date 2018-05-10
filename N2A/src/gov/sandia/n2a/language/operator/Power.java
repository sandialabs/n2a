/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

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

    public void determineExponent (Variable from)
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        // See notes on Exp.determineExponent()
        // If the second operand is negative, the user must specify a hint.
        // TODO: turn this OperatorBinary into a Function, so it can receive fixed-point hint

        if (operand0.exponent != Integer.MIN_VALUE  &&  operand1.exponent != Integer.MIN_VALUE)
        {
            double b;
            if (operand0 instanceof Constant)
            {
                b = ((Scalar) ((Constant) operand0).value).value;
            }
            else
            {
                b = Math.pow (2, operand0.exponent + 1) - 1;
            }

            double a;
            if (operand1 instanceof Constant)
            {
                a = ((Scalar) ((Constant) operand1).value).value;
            }
            else
            {
                a = Math.pow (2, operand1.exponent + 1) - 1;
            }

            // let p = our exponent (result of power function)
            // p = log2(b^a) = a*log2(b)
            updateExponent (from, (int) Math.floor (a * Math.log (b) / Math.log (2)));
        }
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
