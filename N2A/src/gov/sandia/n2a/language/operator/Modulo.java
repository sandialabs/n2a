/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
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

    public void determineExponent (Variable from)
    {
        // TODO: it might be worth shifting a few bits to align the operands, because that case is cheap to compute.
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            // The most precise answer is smaller than the magnitude of either operand.
            int center0 = operand0.centerPower ();
            int center1 = operand1.centerPower ();
            int cent = MSB / 2;
            int pow  = Math.min (center0, center1);
            pow += MSB - cent;
            updateExponent (from, pow, cent);
        }
        else if (operand0.exponent != UNKNOWN)
        {
            updateExponent (from, operand0.exponent, operand0.center);
        }
        else  // operand1.exponent != UNKNOWN
        {
            updateExponent (from, operand1.exponent, operand1.center);
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
