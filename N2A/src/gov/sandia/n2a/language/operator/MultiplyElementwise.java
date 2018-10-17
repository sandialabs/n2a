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
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class MultiplyElementwise extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "&";
            }

            public Operator createInstance ()
            {
                return new MultiplyElementwise ();
            }
        };
    }

    public int precedence ()
    {
        return 4;
    }

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        if (operand0 instanceof Constant)
        {
            Type c0 = ((Constant) operand0).value;
            if (c0 instanceof Scalar)
            {
                double value = ((Scalar) c0).value;
                if (value == 1)
                {
                    from.changed = true;
                    operand1.parent = parent;
                    return operand1;
                }
            }
        }
        else if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar)
            {
                double value = ((Scalar) c1).value;
                if (value == 1)
                {
                    from.changed = true;
                    operand0.parent = parent;
                    return operand0;
                }
            }
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            int cent = MSB / 2;
            int pow = operand0.centerPower () + operand1.centerPower ();
            pow += MSB - cent;
            updateExponent (from, pow, cent);
        }
        // else don't to propagate a bad guess. Instead, hope that better information arrives in next cycle.
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
            unit = UnitValue.simplify (operand0.unit.multiply (operand1.unit));
        }
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).multiplyElementwise (operand1.eval (context));
    }

    public String toString ()
    {
        return "&";
    }
}
