/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

/**
    Function equivalent of Power operator.
    Exists only to allow user to specify fixed-point hint. Otherwise, it is sufficient to use b^a.
**/
public class Pow extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "pow";
            }

            public Operator createInstance ()
            {
                return new Pow ();
            }
        };
    }

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        Operator o1 = operands[1];
        if (o1 instanceof Constant)
        {
            Type c1 = ((Constant) o1).value;
            if (c1 instanceof Scalar  &&  ((Scalar) c1).value == 1)
            {
                from.changed = true;
                return operands[0];
            }
            // It would be nice to simplify out x^0. However, if x==0 at runtime, the correct answer is NaN rather than 1.
            // Since we can't know x ahead of time, and NaN may be important to correct processing, we don't touch that case.
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        Operator operand0 = operands[0];
        Operator operand1 = operands[1];
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        // This function is b^a, where b is the base and a is the power.
        // let p = base 2 power of our result
        // p = log2(b^a) = a*log2(b)
        // See notes on Exp.determineExponent()
        // If the second operand is negative, the user must specify a hint.

        int centerNew   = MSB / 2;
        int exponentNew = UNKNOWN;
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

            exponentNew = 0;
            if (log2b != 0  &&  a != 0) exponentNew = (int) Math.floor (a * log2b);
        }
        if (operands.length >= 3) exponentNew = getExponentHint (operands[2].getString (), exponentNew);
        if (exponentNew != UNKNOWN)
        {
            exponentNew += MSB - centerNew;
            updateExponent (from, exponentNew, centerNew);
        }
    }

    public Type eval (Instance context)
    {
        return operands[0].eval (context).power (operands[1].eval (context));
    }

    public String toString ()
    {
        return "pow";
    }
}
