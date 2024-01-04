/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Operator;

public class Draw2D extends Draw
{
    public void determineExponentNext ()
    {
        super.determineExponentNext ();

        // Last arg is color, which is always a raw integer.
        int last = operands.length - 1;
        Operator c = operands[last];
        c.exponentNext = MSB;
        c.determineExponentNext ();

        // All pixel-valued operands must agree on exponent.
        if (last > 1)
        {
            int avg = 0;
            for (int i = 1; i < last; i++) avg += operands[i].exponent;
            avg /= last - 1;
            for (int i = 1; i < last; i++)
            {
                Operator op = operands[i];
                op.exponentNext = avg;
                op.determineExponentNext ();
            }
        }
    }
}
