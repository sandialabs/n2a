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

public class Add extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "+";
            }

            public Operator createInstance ()
            {
                return new Add ();
            }
        };
    }

    public int precedence ()
    {
        return 5;
    }

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        if (operand0 instanceof Constant)
        {
            Type c0 = ((Constant) operand0).value;
            if (c0 instanceof Scalar  &&  ((Scalar) c0).value == 0)
            {
                from.changed = true;
                operand1.parent = parent;
                return operand1;
            }
        }
        else if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar  &&  ((Scalar) c1).value == 0)
            {
                from.changed = true;
                operand0.parent = parent;
                return operand0;
            }
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand0.exponentNext = exponent;
        operand1.exponentNext = exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);
        alignExponent (from);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            int pow = (operand0.exponent + operand1.exponent) / 2;
            int c0 = operand0.center - (pow - operand0.exponent);
            int c1 = operand1.center - (pow - operand1.exponent);
            int cent = Math.max (c0, c1);
            updateExponent (from, pow, cent);
        }
        else if (operand0.exponent != UNKNOWN)
        {
            updateExponent (from, operand0.exponent, operand0.center);
        }
        else if (operand1.exponent != UNKNOWN)
        {
            updateExponent (from, operand1.exponent, operand1.center);
        }
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).add (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs, boolean right)
    {
        Subtract result = new Subtract ();
        result.operand0 = rhs;
        result.operand1 = lhs;
        return result;
    }

    public String toString ()
    {
        return "+";
    }
}
