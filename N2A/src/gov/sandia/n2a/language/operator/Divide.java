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

public class Divide extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "/";
            }

            public Operator createInstance ()
            {
                return new Divide ();
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

        if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar  &&  ((Scalar) c1).value == 1)
            {
                from.changed = true;
                return operand0;
            }
        }
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
            updateExponent (from, operand0.exponent - operand1.exponent);
        }
        // else don't to propagate a bad guess. Instead, hope that better information arrives in next cycle.
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).divide (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs, boolean right)
    {
        if (right)
        {
            Multiply result = new Multiply ();
            result.operand0 = rhs;
            result.operand1 = lhs;
            return result;
        }

        Divide result = new Divide ();
        result.operand0 = lhs;
        result.operand1 = rhs;
        return result;
    }

    public String toString ()
    {
        return "/";
    }
}
