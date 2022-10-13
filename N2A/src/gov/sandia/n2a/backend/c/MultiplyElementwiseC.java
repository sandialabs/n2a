/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.language.operator.MultiplyElementwise;

public class MultiplyElementwiseC extends MultiplyElementwise
{
    public MultiplyElementwiseC (MultiplyElementwise m)
    {
        operand0 = m.operand0;
        operand1 = m.operand1;

        parent          = m.parent;
        operand0.parent = this;
        operand1.parent = this;

        exponent     = m.exponent;
        exponentNext = m.exponentNext;

        // These two shouldn't be necessary at the compile stage where this op
        // is substituted in, but just being thorough.
        unit   = m.unit;
        center = m.center;
    }

    public int precedence ()
    {
        return 8;
    }
}
