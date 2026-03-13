/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

public class Region extends Matrix
{
    protected Matrix A;
    protected int    offsetRow;
    protected int    offsetColumn;
    protected int    rows;
    protected int    columns;

    public Region (Matrix A, int firstRow, int firstColumn, int lastRow, int lastColumn)
    {
        this.A            = A;
        this.offsetRow    = firstRow;
        this.offsetColumn = firstColumn;
        this.rows         = lastRow    - firstRow    + 1;
        this.columns      = lastColumn - firstColumn + 1;
    }

    public int rows ()
    {
        return rows;
    }

    public int columns ()
    {
        return columns;
    }

    public double get (int row, int column)
    {
        return A.get (row + offsetRow, column + offsetColumn);
    }

    public void set (int row, int column, double a)
    {
        A.set (row + offsetRow, column + offsetColumn, a);
    }

    public Matrix clear (double initialValue)
    {
        return new MatrixDense (rows, columns, initialValue);
    }

    public Matrix identity ()
    {
        MatrixDense result = new MatrixDense (rows, columns);
        int h = Math.min (rows, columns);
        for (int r = 0; r < h; r++) result.data[r * (columns + 1)] = 1;
        return result;
    }

    public IteratorNonzero getIteratorNonzero ()
    {
        if (A instanceof MatrixSparse) return new MatrixSparse.IteratorSparse ((MatrixSparse) A, offsetRow, offsetColumn);
        return super.getIteratorNonzero ();
    }
}
