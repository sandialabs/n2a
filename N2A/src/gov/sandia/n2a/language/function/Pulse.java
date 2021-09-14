/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import javax.measure.Unit;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;

public class Pulse extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "pulse";
            }

            public Operator createInstance ()
            {
                return new Pulse ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++)
        {
            operands[i].determineExponent (context);
        }
        updateExponent (context, 0,  MSB - 2); // Output reaches exactly 1.
    }

    public void determineExponentNext ()
    {
        // Get our working exponent from the time variable.
        // This assumes that exponent of $t will be imposed on the first operand.
        Operator op0 = operands[0];
        op0.exponentNext = op0.exponent;
        op0.determineExponentNext ();

        for (int i = 1; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = op0.exponent;
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;

        Unit<?> temp = null;
        for (int i = 0; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.determineUnit (fatal);
            if (op.unit != null)
            {
                if (temp == null  ||  temp.isCompatible (AbstractUnit.ONE))
                {
                    temp = op.unit;
                }
                else if (fatal  &&  ! op.unit.isCompatible (AbstractUnit.ONE)  &&  ! op.unit.isCompatible (temp))
                {
                    throw new Exception (toString () + "(" + temp + " versus " + op.unit + ")");
                }
            }
        }
    }

    public Type eval (Instance context)
    {
        double t      = ((Scalar) operands[0].eval (context)).value;
        double width  = Double.POSITIVE_INFINITY;
        double period = 0;
        double rise   = 0;
        double fall   = 0;
        if (operands.length > 1) width  = ((Scalar) operands[1].eval (context)).value;
        if (operands.length > 2) period = ((Scalar) operands[2].eval (context)).value;
        if (operands.length > 3) rise   = ((Scalar) operands[3].eval (context)).value;
        if (operands.length > 4) fall   = ((Scalar) operands[4].eval (context)).value;

        if (t < 0) return new Scalar (0);
        if (period != 0) t %= period;
        if (t < rise) return new Scalar (t / rise);
        t -= rise;
        if (t < width) return new Scalar (1);
        t -= width;
        if (t < fall) return new Scalar (1.0 - t / fall);
        return new Scalar (0);
    }

    public String toString ()
    {
        return "pulse";
    }
}
