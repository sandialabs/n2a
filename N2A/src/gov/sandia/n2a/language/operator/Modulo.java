/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Instance;

public class Modulo extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "%";
            }

            public Operator createInstance ()
            {
                return new Modulo ();
            }
        };
    }

    public int precedence ()
    {
        return 4;
    }

    public void determineExponent (ExponentContext context)
    {
        // TODO: it might be worth shifting a few bits (in a custom determineExponentNext) to align the operands, because that case is cheap to compute at runtime.
        operand0.determineExponent (context);
        operand1.determineExponent (context);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            // The most precise answer is smaller than the magnitude of either operand.
            int pow         = Math.min (operand0.exponent, operand1.exponent);
            int centerPower = Math.min (operand0.centerPower (), operand1.centerPower ());
            int cent = centerPower - pow;
            updateExponent (context, pow, cent);
        }
        else if (operand0.exponent != UNKNOWN)
        {
            updateExponent (context, operand0.exponent, operand0.center);
        }
        else  // operand1.exponent != UNKNOWN
        {
            updateExponent (context, operand1.exponent, operand1.center);
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operand0.determineUnit (fatal);
        operand1.determineUnit (fatal);
        if (operand0.unit == null  ||  operand1.unit == null)
        {
            unit = null;
        }
        else
        {
            unit = UnitValue.simplify (operand0.unit.divide (operand1.unit));
        }
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).modulo (operand1.eval (context));
    }

    public String toString ()
    {
        return "%";
    }
}
