/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Norm extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "norm";
            }

            public Operator createInstance ()
            {
                return new Norm ();
            }
        };
    }

    public Type eval (Instance context)
    {
        double n = ((Scalar) operands[0].eval (context)).value;
        Matrix A =  (Matrix) operands[1].eval (context);
        return new Scalar (A.norm (n));
    }

    public String toString ()
    {
        return "norm";
    }
}
