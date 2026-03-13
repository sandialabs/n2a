/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

public class Transpose extends Matrix
{
    public Matrix A;

    public Transpose (Matrix A)
    {
        this.A = A;
    }

    public int rows ()
    {
        return A.columns ();
    }

    public int columns ()
    {
        return A.rows ();
    }

    public double get (int row, int column)
    {
        return A.get (column, row);
    }

    public void set (int row, int column, double a)
    {
        A.set (column, row, a);
    }

    public Matrix clear (double initialValue)
    {
        return new MatrixDense (A.columns (), A.rows (), initialValue);
    }

    public Matrix identity ()
    {
        int rows    = A.columns ();
        int columns = A.rows ();
        MatrixDense result = new MatrixDense (rows, columns);
        int h = Math.min (rows, columns);
        for (int r = 0; r < h; r++) result.data[r * (columns + 1)] = 1;
        return result;
    }
}
