/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.io.File;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class ReadMatrix extends Function
{
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
        Matrix A;
        String path = ((Text) operands[0].eval (context)).value;
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return new Scalar (0);  // absence of simulator indicates analysis phase, so opening files is unecessary
        A = simulator.matrices.get (path);
        if (A == null)
        {
            A = new Matrix (new File (path).getAbsoluteFile ());
            simulator.matrices.put (path, A);
        }

        int rows    = A.rows ();
        int columns = A.columns ();
        int lastRow    = rows    - 1;
        int lastColumn = columns - 1;
        double row    = ((Scalar) operands[1].eval (context)).value;
        double column = ((Scalar) operands[2].eval (context)).value;

        boolean raw = false;
        if (operands.length > 3)
        {
            String method = ((Text) operands[3].eval (context)).value;
            if (method.equals ("raw")) raw = true;
        }

        if (raw)
        {
            int r = (int) Math.floor (row);
            int c = (int) Math.floor (column);
            if      (r < 0    ) r = 0;
            else if (r >= rows) r = lastRow;
            if      (c < 0       ) c = 0;
            else if (c >= columns) c = lastColumn;
            return A.getScalar (r, c);
        }
        else
        {
            row    *= lastRow;
            column *= lastColumn;
            int r = (int) Math.floor (row);
            int c = (int) Math.floor (column);
            if (r < 0)
            {
                if      (c <  0         ) return A.getScalar (0, 0         );
                else if (c >= lastColumn) return A.getScalar (0, lastColumn);
                else
                {
                    double b = column - c;
                    return new Scalar ((1 - b) * A.getDouble (0, c) + b * A.getDouble (0, c+1));
                }
            }
            else if (r >= lastRow)
            {
                if      (c <  0         ) return A.getScalar (lastRow, 0         );
                else if (c >= lastColumn) return A.getScalar (lastRow, lastColumn);
                else
                {
                    double b = column - c;
                    return new Scalar ((1 - b) * A.getDouble (lastRow, c) + b * A.getDouble (lastRow, c+1));
                }
            }
            else
            {
                double a = row - r;
                double a1 = 1 - a;
                if      (c <  0         ) return new Scalar (a1 * A.getDouble (r, 0         ) + a * A.getDouble (r+1, 0         ));
                else if (c >= lastColumn) return new Scalar (a1 * A.getDouble (r, lastColumn) + a * A.getDouble (r+1, lastColumn));
                else
                {
                    double b = column - c;
                    return new Scalar (  (1 - b) * (a1 * A.getDouble (r, c  ) + a * A.getDouble (r+1, c  ))
                                       +      b  * (a1 * A.getDouble (r, c+1) + a * A.getDouble (r+1, c+1)));
                }
            }
        }
    }

    public String toString ()
    {
        return "matrix";
    }
}
