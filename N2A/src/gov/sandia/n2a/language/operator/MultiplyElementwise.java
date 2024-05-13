/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Instance;

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

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        if (operand0.isScalar ()  &&  operand0.getDouble () == 1)
        {
            from.changed = true;
            operand1.parent = parent;
            return operand1;
        }
        else if (operand1.isScalar ()  &&  operand1.getDouble () == 1)
        {
            from.changed = true;
            operand0.parent = parent;
            return operand0;
        }
        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        operand0.determineExponent (context);
        operand1.determineExponent (context);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            int cent = MSB / 2;
            int pow = operand0.centerPower () + operand1.centerPower ();
            pow -= cent;
            updateExponent (context, pow, cent);
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
