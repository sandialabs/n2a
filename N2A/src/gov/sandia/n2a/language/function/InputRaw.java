/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class InputRaw extends Input  // inherit from Input because most of the heavy processing is already implemented there
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "inputRaw";
            }

            public Operator createInstance ()
            {
                return new InputRaw ();
            }
        };
    }

    public Type eval (Instance context)
    {
        Holder H = getRow (context);
        if (H == null) return new Scalar (0);

        int columns = H.values.length;
        int c = (int) Math.floor (((Scalar) operands[2].eval (context)).value);
        if      (c < 0       ) c = 0;
        else if (c >= columns) c = columns - 1;
        return new Scalar (H.values[c]);
    }

    public String toString ()
    {
        return "inputRaw";
    }
}
