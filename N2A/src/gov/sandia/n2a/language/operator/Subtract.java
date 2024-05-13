/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class Subtract extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "-";
            }

            public Operator createInstance ()
            {
                return new Subtract ();
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

        if (operand0.isScalar ()  &&  operand0.getDouble () == 0)  // 0-B --> -B
        {
            from.changed = true;
            Negate n = new Negate ();
            n.parent = parent;
            n.operand = operand1;
            n.operand.parent = n;
            return n;
        }
        else if (operand1.isScalar ()  &&  operand1.getDouble () == 0)  // A-0 --> A
        {
            from.changed = true;
            operand0.parent = parent;
            return operand0;
        }
        else if (operand1 instanceof AccessVariable)
        {
            if (operand0 instanceof AccessVariable)  // A-A --> 0
            {
                AccessVariable av0 = (AccessVariable) operand0;
                AccessVariable av1 = (AccessVariable) operand1;
                if (av0.reference.equals (av1.reference))
                {
                    from.changed = true;
                    if (! evalOnly) releaseDependencies (from);
                    result = new Constant (0);
                    result.parent = parent;
                    return result;
                }
            }
        }
        else if (operand1 instanceof Negate)  // A-(-B) --> A+B
        {
            from.changed = true;
            Add a = new Add ();
            a.parent = parent;
            a.operand0 = operand0;
            a.operand0.parent = a;
            a.operand1 = ((Negate) operand1).operand;
            a.operand1.parent = a;
            return a;
        }
        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        operand0.determineExponent (context);
        operand1.determineExponent (context);

        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            alignExponent (context);

            int pow = (operand0.exponent + operand1.exponent) / 2;
            // Call an odd bit in favor of a naked variable rather than the expression on the other side of the operator.
            if      (operand0 instanceof AccessVariable  &&  Math.abs (pow - operand0.exponent) == 1) pow = operand0.exponent;
            else if (operand1 instanceof AccessVariable  &&  Math.abs (pow - operand1.exponent) == 1) pow = operand1.exponent;

            int c0 = operand0.center - (pow - operand0.exponent);
            int c1 = operand1.center - (pow - operand1.exponent);
            int cent = Math.max (c0, c1);
            int min  = Math.min (c0, c1);
            if (cent >= MSB)
            {
                // The most optimistic thing we could hope for is that value only exceeds center by 1 bit.
                pow += cent - (MSB - 1);
                cent = MSB - 1;
            }
            else if (min < 0)
            {
                pow  += min;  // decreases pow
                cent -= min;  // increases cent
            }
            updateExponent (context, pow, cent);
        }
        else if (operand0.exponent != UNKNOWN)
        {
            updateExponent (context, operand0.exponent, operand0.center);
        }
        else if (operand1.exponent != UNKNOWN)
        {
            updateExponent (context, operand1.exponent, operand1.center);
        }
    }

    public void determineExponentNext ()
    {
        int next = exponent;  // The default
        if (parent instanceof Variable)  // Avoid shifting a variable just before assigning back to itself. Also avoid this in the condition, even though there is no assignment.
        {
            if (   operand0 instanceof AccessVariable  &&  ((AccessVariable) operand0).reference.variable == parent
                || operand1 instanceof AccessVariable  &&  ((AccessVariable) operand1).reference.variable == parent)
            {
                next = ((Variable) parent).exponent;
            }
        }
        operand0.exponentNext = next;
        operand1.exponentNext = next;
        operand0.determineExponentNext ();
        operand1.determineExponentNext ();
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;

        // As a matter of style, we don't add any spaces around binary operators.
        // However, in the special case of minus followed by a negative constant, we add the space
        // for clarity, and to prevent compilation errors in C++.
        String middle = "-";
        if (operand1.getDouble () < 0) middle = "- ";
        render (renderer, middle);
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).subtract (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs)
    {
        if (lhs == operand1)
        {
            Add result = new Add ();
            result.operand0 = rhs;
            result.operand1 = lhs;
            return result;
        }

        Subtract result = new Subtract ();
        result.operand0 = lhs;
        result.operand1 = rhs;
        return result;
    }

    public String toString ()
    {
        return "-";
    }
}
