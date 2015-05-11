/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.io.File;
import java.util.HashMap;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class ReadMatrix extends Function
{
    public HashMap<String,Matrix> matrices = new HashMap<String,Matrix> ();  // TODO: in an interpreter, need some way to reset cached data at start of each run

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "matrix";
            }

            public Operator createInstance ()
            {
                return new ReadMatrix ();
            }
        };
    }

    public Type eval (Instance context)
    {
        double row    = ((Scalar) operands[1].eval (context)).value;
        double column = ((Scalar) operands[2].eval (context)).value;
        if (row < 0  ||  row > 1  ||  column < 0  ||  column > 1) return new Scalar (0);

        String path = ((Text) operands[0].eval (context)).value;
        if (! matrices.containsKey (path)) matrices.put (path, new Matrix (new File (path)));
        Matrix A = matrices.get (path);

        int rows    = A.rows ();
        int columns = A.columns ();
        row    = row    * rows    - 0.5;
        column = column * columns - 0.5;
        int r = (int) Math.floor (row);
        int c = (int) Math.floor (column);
        if (r < 0)
        {
            if      (c < 0)            return A.getScalar (r+1, c+1);
            else if (c >= columns - 1) return A.getScalar (r+1, c  );
            else
            {
                double b = column - c;
                return new Scalar ((1 - b) * A.getDouble (r+1, c) + b * A.getDouble (r+1, c+1));
            }
        }
        else if (r >= rows - 1)
        {
            if      (c < 0)            return A.getScalar (r, c+1);
            else if (c >= columns - 1) return A.getScalar (r, c  );
            else
            {
                double b = column - c;
                return new Scalar ((1 - b) * A.getDouble (r+1, c) + b * A.getDouble (r+1, c+1));
            }
        }
        else
        {
            double a = row - r;
            double a1 = 1 - a;
            if      (c < 0)            return new Scalar (a1 * A.getDouble (r, c+1) + a * A.getDouble (r+1, c+1));
            else if (c >= columns - 1) return new Scalar (a1 * A.getDouble (r, c  ) + a * A.getDouble (r+1, c  ));
            else
            {
                double b = column - c;
                return new Scalar (  (1 - b) * (a1 * A.getDouble (r, c  ) + a * A.getDouble (r+1, c  ))
                                   +      b  * (a1 * A.getDouble (r, c+1) + a * A.getDouble (r+1, c+1)));
            }
        }
    }

    public String toString ()
    {
        return "matrix";
    }
}
