/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.functions;

import gov.sandia.n2a.language.Function;

public class GridFunction extends Function
{
    public GridFunction ()
    {
        name          = "grid";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args)
    {
        int row = ((Number)args[6]).intValue() / ((Number)args[0]).intValue();
        int col = ((Number)args[6]).intValue() % ((Number)args[1]).intValue();
        // let's simplify things by assuming one corner of the space is
        // at (0, 0), and having the distance to the edge of the defined space
        // be the same as the distance between points within the space
        double xStride = ((Number)args[3]).doubleValue() / (((Number)args[0]).intValue() + 1);
        double yStride = ((Number)args[4]).doubleValue() / (((Number)args[1]).intValue() + 1);
        double xPosition = (col+1) * xStride;
        double yPosition = (row+1) * yStride;
        Number[] result = {xPosition,yPosition};
        return result;
    }
}
