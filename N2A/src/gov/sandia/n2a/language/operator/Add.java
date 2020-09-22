/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Add extends OperatorBinary
{
    public String name;  // For C backend, the name of the variable that holds an assembled result when Add is used to concatenate strings.

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

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
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
        operand0.determineExponent (from);
        operand1.determineExponent (from);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            alignExponent (from);

            int pow = (operand0.exponent + operand1.exponent) / 2;
            // Call an odd bit in favor of a naked variable rather than the expression on the other side of the operator.
            if      (operand0 instanceof AccessVariable  &&  Math.abs (pow - operand0.exponent) == 1) pow = operand0.exponent;
            else if (operand1 instanceof AccessVariable  &&  Math.abs (pow - operand1.exponent) == 1) pow = operand1.exponent;

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

    public void determineExponentNext (Variable from)
    {
        int next = exponent;  // The default
        if (parent == null  ||  parent instanceof Variable)  // top-level operator, about to assign to a variable
        {
            if (   operand0 instanceof AccessVariable  &&  ((AccessVariable) operand0).reference.variable == from
                || operand1 instanceof AccessVariable  &&  ((AccessVariable) operand1).reference.variable == from)
            {
                next = from.exponent;
            }
        }
        operand0.exponentNext = next;
        operand1.exponentNext = next;
        operand0.determineExponentNext (from);
        operand1.determineExponentNext (from);
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).add (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs)
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
