/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;

public class Comparison extends OperatorBinary implements OperatorLogical
{
    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        // Determine if both operands are exactly the same expression.
        // In this case, our value can be constant, even if the operands themselves are not.
        if (operand0.render ().equals (operand1.render ()))  // This method is crude, but should be sufficient for simple cases.
        {
            from.changed = true;
            if (! evalOnly) releaseDependencies (from);
            operand0 = operand1 = new Constant (new Scalar (0));
            result = new Constant (eval (null));
            result.parent = parent;
            return result;
        }

        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        operand0.determineExponent (context);
        operand1.determineExponent (context);
        if (operand0.exponent != UNKNOWN  ||  operand1.exponent != UNKNOWN) alignExponent (context);
        int centerNew   = MSB / 2;
        int exponentNew = MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);

        // Any time a variable is compared to a value, it is a clue about the expected range of the variable.
        // The following two blocks apply this heuristic.

        if (operand0 instanceof AccessVariable)
        {
            Variable v = ((AccessVariable) operand0).reference.variable;
            if (! v.hasAttribute ("preexistent")  &&  (v.bound == null  ||  v.bound.centerPower () < operand1.centerPower ()))
            {
                v.bound = operand1;
                context.changed = true;  // Signal that some change happened, though not necessarily to current variable (context.from).
            }
        }

        if (operand1 instanceof AccessVariable)
        {
            Variable v = ((AccessVariable) operand1).reference.variable;
            if (! v.hasAttribute ("preexistent")  &&  (v.bound == null  ||  v.bound.centerPower () < operand0.centerPower ()))
            {
                v.bound = operand0;
                context.changed = true;
            }
        }
    }

    public void determineExponentNext ()
    {
        int next = (operand0.exponent + operand1.exponent) / 2;
        // Call an odd bit in favor of a naked variable rather than the expression on the other side of the comparison.
        if      (operand0 instanceof AccessVariable  &&  Math.abs (next - operand0.exponent) == 1) next = operand0.exponent;
        else if (operand1 instanceof AccessVariable  &&  Math.abs (next - operand1.exponent) == 1) next = operand1.exponent;

        operand0.exponentNext = next;
        operand1.exponentNext = next;
        operand0.determineExponentNext ();
        operand1.determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        super.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        if (type == null) type = new Scalar ();
        return type;
    }
}
