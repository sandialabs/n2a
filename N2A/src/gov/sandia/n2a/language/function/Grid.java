/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.util.ArrayList;

import gov.sandia.n2a.language.Function;

public class Grid extends Function
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

    public Object eval (Object[] args)
    {
        // collect parameters into arrays
        int index = ((Number) args[0]).intValue ();
        int[] stride = new int[3];
        stride[0] = ((Number) args[1]).intValue ();
        stride[1] = ((Number) args[2]).intValue ();
        stride[2] = ((Number) args[3]).intValue ();
        double[] length = new double[3];
        length[0] = ((Number) args[4]).doubleValue ();
        length[1] = ((Number) args[5]).doubleValue ();
        length[2] = ((Number) args[6]).doubleValue ();

        // sort by largest stride
        int[] major = new int[3];
        major[0] = 0;
        major[1] = 1;
        major[2] = 2;
        if (stride[major[0]] < stride[major[1]]) swap (major, 0, 1);
        if (stride[major[1]] < stride[major[2]]) swap (major, 1, 2);
        if (stride[major[0]] < stride[major[1]]) swap (major, 0, 1);

        // compute xyz in stride order
        Number[] result = new Number[3];
        for (int i = 0; i < 3; i++)
        {
            int m = major[i];
            result[m] = (index / stride[m]) * length[m];  // The division is an integer operation, so remainder is truncated.
            index %= stride[m];
        }
        return result;
    }
}
