/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class LE extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "<=";
            }

            public Operator createInstance ()
            {
                return new LE ();
            }
        };
    }

    public int precedence ()
    {
        return 6;
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).LE (operand1.eval (context));
    }

    public String toString ()
    {
        return "<=";
    }
}
