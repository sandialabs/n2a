/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Grid extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "grid";
            }

            public Operator createInstance ()
            {
                return new Grid ();
            }
        };
    }

    public Type eval (Instance context) throws EvaluationException
    {
        // collect parameters into arrays
        int i = (int) Math.floor (((Scalar) operands[0].eval (context)).value);
        int nx = 1;
        int ny = 1;
        int nz = 1;
        if (operands.length >= 2) nx = (int) Math.floor (((Scalar) operands[1].eval (context)).value);
        if (operands.length >= 3) nx = (int) Math.floor (((Scalar) operands[2].eval (context)).value);
        if (operands.length >= 4) nx = (int) Math.floor (((Scalar) operands[3].eval (context)).value);
        int sx = ny * nz;  // stride x

        // compute xyz in stride order
        Matrix result = new Matrix (3, 1);
        result.value[0][0] = ((i / sx) + 0.5) / nx;  // (i / sx) is an integer operation, so remainder is truncated.
        i %= sx;
        result.value[0][1] = ((i / nz) + 0.5) / ny;
        result.value[0][2] = ((i % nz) + 0.5) / nz;
        return result;
    }

    public String toString ()
    {
        return "grid";
    }
}
