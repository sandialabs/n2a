/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;
import gov.sandia.n2a.language.type.Matrix;

public class Equal extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "equal";
            }

            public Operator createInstance ()
            {
                return new Equal ();
            }
        };
    }

    // The implementations here must recapitulate most of what Comparison does.
    // In particular, it should work like operator.EQ, except doing whole-matrix comparison.

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        // Determine if both operands are exactly the same expression.
        // In this case, our value can be constant, even if the operands themselves are not.
        if (operands[0].render ().equals (operands[1].render ()))  // This method is crude, but should be sufficient for simple cases.
        {
            from.changed = true;
            if (! evalOnly) releaseDependencies (from);
            result = new Constant (1);
            result.parent = parent;
            return result;
        }

        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        Operator op0 = operands[0];
        Operator op1 = operands[1];

        op0.determineExponent (context);
        op1.determineExponent (context);
        if (op0.exponent != UNKNOWN  ||  op1.exponent != UNKNOWN)
        {
            // See OperatorBinary.alignExponent()
            if      (op0 instanceof Constant) ((Constant) op0).determineExponent (context, op1.exponent);
            else if (op1 instanceof Constant) ((Constant) op1).determineExponent (context, op0.exponent);
        }
        updateExponent (context, MSB, 0);  // Output is 1 or 0

        // Any time a variable is compared to a value, it is a clue about the expected range of the variable.
        // The following two blocks apply this heuristic.

        if (op0 instanceof AccessVariable)
        {
            Variable v = ((AccessVariable) op0).reference.variable;
            if (! v.hasAttribute ("preexistent")  &&  (v.bound == null  ||  v.bound.centerPower () < op1.centerPower ()))
            {
                v.bound = op1;
                context.changed = true;  // Signal that some change happened, though not necessarily to current variable (context.from).
            }
        }

        if (op1 instanceof AccessVariable)
        {
            Variable v = ((AccessVariable) op1).reference.variable;
            if (! v.hasAttribute ("preexistent")  &&  (v.bound == null  ||  v.bound.centerPower () < op0.centerPower ()))
            {
                v.bound = op0;
                context.changed = true;
            }
        }
    }

    public void determineExponentNext ()
    {
        Operator op0 = operands[0];
        Operator op1 = operands[1];

        int next = (op0.exponent + op1.exponent) / 2;
        // Call an odd bit in favor of a naked variable rather than the expression on the other side of the comparison.
        if      (op0 instanceof AccessVariable  &&  Math.abs (next - op0.exponent) == 1) next = op0.exponent;
        else if (op1 instanceof AccessVariable  &&  Math.abs (next - op1.exponent) == 1) next = op1.exponent;

        op0.exponentNext = next;
        op1.exponentNext = next;
        op0.determineExponentNext ();
        op1.determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        super.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Matrix A = (Matrix) operands[0].eval (context);
        Matrix B = (Matrix) operands[1].eval (context);
        return new Scalar (A.equals (B) ? 1 : 0);
    }

    public String toString ()
    {
        return "equal";
    }
}
