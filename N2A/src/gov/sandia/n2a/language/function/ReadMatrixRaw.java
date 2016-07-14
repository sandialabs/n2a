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

public class ReadMatrixRaw extends Function
{
    public HashMap<String,Matrix> matrices = new HashMap<String,Matrix> ();

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "matrixRaw";
            }

            public Operator createInstance ()
            {
                return new ReadMatrixRaw ();
            }
        };
    }

    public Type eval (Instance context)
    {
        String path = ((Text) operands[0].eval (context)).value;
        if (! matrices.containsKey (path)) matrices.put (path, new Matrix (new File (path).getAbsoluteFile ()));  // getAbsoluteFile() interprets path relative to System user.dir
        Matrix A = matrices.get (path);

        int rows    = A.rows ();
        int columns = A.columns ();
        int r = (int) Math.floor (((Scalar) operands[1].eval (context)).value);
        int c = (int) Math.floor (((Scalar) operands[2].eval (context)).value);
        if      (r < 0    ) r = 0;
        else if (r >= rows) r = rows - 1;
        if      (c < 0       ) c = 0;
        else if (c >= columns) c = columns - 1;
        return A.getScalar (r, c);
    }

    public String toString ()
    {
        return "matrixRaw";
    }
}
