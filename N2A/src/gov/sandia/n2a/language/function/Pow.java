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

        int exponentNew = Integer.MIN_VALUE;
        if (operand0.exponent != Integer.MIN_VALUE  &&  operand1.exponent != Integer.MIN_VALUE)
        {
            double log2b = 0;
            if (operand0 instanceof Constant)
            {
                double b = operand0.getDouble ();
                if (b != 0) log2b = Math.log (b) / Math.log (2);
            }
            else
            {
                log2b = operand0.exponent - MSB + operand0.center;
            }

            double a;
            if (operand1 instanceof Constant) a = operand1.getDouble ();
            else                              a = Math.pow (2, operand1.centerPower ());

            if (log2b == 0  ||  a == 0) exponentNew = MSB / 2 - 1;
            else                        exponentNew = (int) Math.floor (a * log2b);
        }
        if (operands.length >= 3) exponentNew = getExponentHint (operands[2].getString (), exponentNew);
        updateExponent (from, exponentNew, MSB / 2 + 1);
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
