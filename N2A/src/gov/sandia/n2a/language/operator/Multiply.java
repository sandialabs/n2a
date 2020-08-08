/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class Multiply extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "*";
            }

            public Operator createInstance ()
            {
                return new Multiply ();
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

        if (operand0.isScalar ())
        {
            double value = operand0.getDouble ();
            if (value == 1)
            {
                from.changed = true;
                operand1.parent = parent;
                return operand1;
            }
            if (value == 0)
            {
                from.changed = true;
                if (! evalOnly) operand1.releaseDependencies (from);
                result = new Constant (0);
                result.parent = parent;
                return result;
            }
        }
        else if (operand1.isScalar ())
        {
            double value = operand1.getDouble ();
            if (value == 1)
            {
                from.changed = true;
                operand0.parent = parent;
                return operand0;
            }
            if (value == 0)
            {
                from.changed = true;
                if (! evalOnly) operand0.releaseDependencies (from);
                result = new Constant (0);
                result.parent = parent;
                return result;
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
        return operand0.eval (context).multiply (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs)
    {
        Divide result = new Divide ();
        result.operand0 = rhs;
        result.operand1 = lhs;
        return result;
    }

    public String toString ()
    {
        return "*";
    }
}
