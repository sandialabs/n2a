/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.type.Scalar;

public class Comparison extends OperatorBinary
{
    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        // Determine if both operands are exactly the same expression.
        // In this case, our value can be constant, even if the operands themselves are not.
        if (operand0.render ().equals (operand1.render ()))  // This method is crude, but should be sufficient for simple cases.
        {
            from.changed = true;
            releaseDependencies (from);
            operand0 = operand1 = new Constant (new Scalar (0));
            return new Constant (eval (null));
        }

        return this;
    }

    public void determineExponent (Variable from)
    {
        int next = Math.max (operand0.exponent, operand1.exponent);
        operand0.exponentNext = next;
        operand1.exponentNext = next;
        operand0.determineExponent (from);
        operand1.determineExponent (from);
        updateExponent (from, MSB, 0);  // Output is boolean, so treat as integer.

        // Any time a variable is compared to a value, it is a clue about the expected range of the variable.
        // The following two blocks apply this heuristic.

        if (operand0 instanceof AccessVariable)
        {
            Variable v = ((AccessVariable) operand0).reference.variable;
            if (! v.hasAttribute ("preexistent")  &&  (v.bound == null  ||  v.bound.centerPower () < operand1.centerPower ()))
            {
                v.bound = operand1;
                from.changed = true;  // Probably "from" is not the variable changing, but this is sufficient to signal that some change happened.
            }
        }

        if (operand1 instanceof AccessVariable)
        {
            Variable v = ((AccessVariable) operand1).reference.variable;
            if (! v.hasAttribute ("preexistent")  &&  (v.bound == null  ||  v.bound.centerPower () < operand0.centerPower ()))
            {
                v.bound = operand0;
                from.changed = true;
            }
        }
    }
}
