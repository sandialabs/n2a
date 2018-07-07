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

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar  &&  ((Scalar) c1).value == 1)
            {
                from.changed = true;
                return operand0;
            }
            // It would be nice to simplify out x^0. However, if x==0 at runtime, the correct answer is NaN rather than 1.
            // Since we can't know x ahead of time, and NaN may be important to correct processing, we don't touch that case.
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        // let p = base 2 power of our result
        // p = log2(b^a) = a*log2(b)
        // See notes on Exp.determineExponent()
        // If the second operand is negative, the user must specify a hint, which requires the use of pow() instead.

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            double log2b = 0;
            if (operand0 instanceof Constant)
            {
                double b = operand0.getDouble ();
                if (b != 0) log2b = Math.log (b) / Math.log (2);
            }
            else
            {
                log2b = operand0.centerPower ();
            }

            double a;
            if (operand1 instanceof Constant) a = operand1.getDouble ();
            else                              a = Math.pow (2, operand1.centerPower ());

            int centerNew   = MSB / 2;
            int exponentNew = 0;
            if (log2b != 0  &&  a != 0) exponentNew = (int) Math.floor (a * log2b);
            exponentNew += MSB - centerNew;
            updateExponent (from, exponentNew, centerNew);
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
