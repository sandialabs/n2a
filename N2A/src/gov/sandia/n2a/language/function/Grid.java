/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Grid extends Operator
{
    public Grid ()
    {
        name          = "grid";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public void swap (int[] array, int i, int j)
    {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    public Type eval (Type[] args)
    {
        // collect parameters into arrays
        int i = (int) Math.round (((Scalar) args[0]).value);
        int nx = 1;
        int ny = 1;
        int nz = 1;
        if (args.length >= 2) nx = (int) Math.round (((Scalar) args[1]).value);
        if (args.length >= 3) nx = (int) Math.round (((Scalar) args[2]).value);
        if (args.length >= 4) nx = (int) Math.round (((Scalar) args[3]).value);
        int sy = ny * nz;
        int sx = nx * sy;

        // compute xyz in stride order
        Matrix result = new Matrix (3, 1);
        result.value[0][0] = ((i / sx) + 0.5) / nx;  // (i / sx) is an integer operation, so remainder is truncated.
        i %= sx;
        result.value[0][1] = ((i / sy) + 0.5) / ny;
        result.value[0][2] = ((i % sy) + 0.5) / nz;
        return result;
    }
}
