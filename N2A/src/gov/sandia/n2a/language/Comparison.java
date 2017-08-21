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
}
