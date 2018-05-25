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

        from.changed = true;  // This will be reversed below if we don't actually make a change.
        if (operand0 instanceof Constant)
        {
            Type c0 = ((Constant) operand0).value;
            if (c0 instanceof Scalar)
            {
                double value = ((Scalar) c0).value;
                if (value == 1) return operand1;
            }
        }
        else if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar)
            {
                double value = ((Scalar) c1).value;
                if (value == 1) return operand0;
            }
        }
        from.changed = false;
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        if (operand0.exponent != Integer.MIN_VALUE  &&  operand1.exponent != Integer.MIN_VALUE)
        {
            int cent = MSB / 2 + 1;
            int pow = operand0.centerPower () + operand1.centerPower ();
            pow += MSB - cent;
            updateExponent (from, pow, cent);
        }
        // else don't to propagate a bad guess. Instead, hope that better information arrives in next cycle.
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
