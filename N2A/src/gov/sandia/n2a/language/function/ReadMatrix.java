/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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

    public boolean canBeConstant ()
    {
        return false;
    }

    public boolean canBeInitOnly ()
    {
        return true;
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return new Scalar (0);  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Matrix A = simulator.matrices.get (path);
        if (A == null)
        {
            A = Matrix.factory (new File (path).getAbsoluteFile ());
            simulator.matrices.put (path, A);
        }

        String mode = "";
        int lastParm = operands.length - 1;
        if (lastParm > 0)
        {
            Type parmValue = operands[lastParm].eval (context);
            if (parmValue instanceof Text) mode = ((Text) parmValue).value;
        }
        if (mode.equals ("columns")) return new Scalar (A.columns ());
        if (mode.equals ("rows"   )) return new Scalar (A.rows    ());

        int rows    = A.rows ();
        int columns = A.columns ();
        int lastRow    = rows    - 1;
        int lastColumn = columns - 1;
        double row    = ((Scalar) operands[1].eval (context)).value;
        double column = ((Scalar) operands[2].eval (context)).value;

        if (mode.equals ("raw"))
        {
            int r = (int) Math.floor (row);
            int c = (int) Math.floor (column);
            if      (r < 0    ) r = 0;
            else if (r >= rows) r = lastRow;
            if      (c < 0       ) c = 0;
            else if (c >= columns) c = lastColumn;
            return new Scalar (A.get (r, c));
        }
        else
        {
            row    *= lastRow;
            column *= lastColumn;
            int r = (int) Math.floor (row);
            int c = (int) Math.floor (column);
            if (r < 0)
            {
                if      (c <  0         ) return new Scalar (A.get (0, 0));
                else if (c >= lastColumn) return new Scalar (A.get (0, lastColumn));
                else
                {
                    double b = column - c;
                    return new Scalar ((1 - b) * A.get (0, c) + b * A.get (0, c+1));
                }
            }
            else if (r >= lastRow)
            {
                if      (c <  0         ) return new Scalar (A.get (lastRow, 0));
                else if (c >= lastColumn) return new Scalar (A.get (lastRow, lastColumn));
                else
                {
                    double b = column - c;
                    return new Scalar ((1 - b) * A.get (lastRow, c) + b * A.get (lastRow, c+1));
                }
            }
            else
            {
                double a = row - r;
                double a1 = 1 - a;
                if      (c <  0         ) return new Scalar (a1 * A.get (r, 0         ) + a * A.get (r+1, 0         ));
                else if (c >= lastColumn) return new Scalar (a1 * A.get (r, lastColumn) + a * A.get (r+1, lastColumn));
                else
                {
                    double b = column - c;
                    return new Scalar (  (1 - b) * (a1 * A.get (r, c  ) + a * A.get (r+1, c  ))
                                       +      b  * (a1 * A.get (r, c+1) + a * A.get (r+1, c+1)));
                }
            }
        }
    }

    public String toString ()
    {
        return "matrix";
    }
}
