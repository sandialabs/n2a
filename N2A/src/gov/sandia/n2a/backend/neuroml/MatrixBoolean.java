/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.ArrayList;
import java.util.List;

public class MatrixBoolean
{
    protected List<boolean[]> data     = new ArrayList<boolean[]> ();
    protected int             rowCount = 0;  // (One more than) the greatest row index that has been touched by either a set() or clear().

    public MatrixBoolean ()
    {
    }

    public MatrixBoolean (int rows, int columns)
    {
        for (int c = 0; c < columns; c++) data.add (new boolean[rows]);
        rowCount = rows;
    }

    public int rows ()
    {
        return rowCount;
    }

    public int columns ()
    {
        return data.size ();
    }

    public boolean get (int row, int col)
    {
        if (col >= data.size ()) return false;
        boolean[] column = data.get (col);
        if (row >= column.length) return false;
        return column[row];
    }

    public void set (int row, int col)
    {
        for (int c = data.size (); c <= col; c++)
        {
            data.add (new boolean[rowCount]);
        }
        boolean[] column = data.get (col);
        if (row >= column.length)
        {
            boolean[] old = column;
            if (row >= rowCount) rowCount = row + 1;
            column = new boolean[rowCount];
            data.set (col, column);
            for (int r = 0; r < old.length; r++) column[r] = old[r];
        }
        column[row] = true;
    }

    public void clear (int row, int col)
    {
        if (row >= rowCount) rowCount = row + 1;
        for (int c = data.size (); c <= col; c++)
        {
            data.add (new boolean[0]);  // unlike set(), we don't allocate full storage
        }
        boolean[] column = data.get (col);
        if (row >= column.length) return;
        column[row] = false;
    }

    /**
        Makes a shallow copy of the requested column.
    **/
    public MatrixBoolean column (int col)
    {
        MatrixBoolean result = new MatrixBoolean ();
        result.data.add (data.get (col));
        return result;
    }

    /**
        Makes a deep copy of the requested row.
    **/
    public MatrixBoolean row (int row)
    {
        MatrixBoolean result = new MatrixBoolean ();
        for (int c = 0; c < data.size (); c++)
        {
            boolean[] column = data.get (c);
            if (row < column.length  &&  column[row]) result.set (0, c);
        }
        return result;
    }

    public int columnNorm0 (int col)
    {
        if (col >= data.size ()) return 0;
        boolean[] column = data.get (col);
        int result = 0;
        for (boolean b : column) if (b) result++;
        return result;
    }

    public int indexInColumn (int row, int col)
    {
        if (col >= data.size ()) return -1;
        boolean[] column = data.get (col);
        int result = 0;
        for (int r = 0; r < row; r++) if (column[r]) result++;
        return result;
    }

    public int firstNonzeroRow (int col)
    {
        if (col >= data.size ()) return -1;
        boolean[] column = data.get (col);
        for (int r = 0; r < column.length; r++) if (column[r]) return r;
        return -1;
    }

    public void OR (int colTo, int colFrom)
    {
        if (colFrom >= data.size ()) return;
        boolean[] column = data.get (colFrom);
        for (int r = 0; r < column.length; r++) if (column[r]) set (r, colTo);
    }

    /**
        Collapse rows with the same pattern into single rows, and compute
        a permutation matrix (of sorts) which can recover the original matrix.
        @param P The permutation matrix. The original matrix is the product PF.
        @param F The simplified ("folded") matrix. Each row is unique.
    **/
    public void foldRows (MatrixBoolean P, MatrixBoolean F)
    {
        int columnCount = data.size ();
        for (int r = 0; r < rowCount; r++)
        {
            // Scan for row in F which matches current row
            int f;
            for (f = 0; f < F.rowCount; f++)
            {
                boolean match = true;
                for (int c = 0; c < columnCount; c++)
                {
                    if (get (r, c) != F.get (f, c))
                    {
                        match = false;
                        break;
                    }
                }
                if (match) break;
            }
            P.set (r, f);
            if (f >= F.rowCount)
            {
                for (int c = 0; c < columnCount; c++) if (get (r, c)) F.set (f, c);
            }
        }
    }

    public MatrixBoolean multiply (MatrixBoolean B)
    {
        MatrixBoolean R = new MatrixBoolean ();
        int widthA =   data.size ();
        int widthB = B.data.size ();
        for (int c = 0; c < widthB; c++)
        {
            boolean[] columnB = B.data.get (c);
            int count = Math.min (widthA, columnB.length);
            for (int k = 0; k < count; k++)
            {
                if (! columnB[k]) continue;
                boolean[] columnA = data.get (k);
                for (int r = 0; r < columnA.length; r++) if (columnA[r]) R.set (r, c);
            }
        }
        return R;
    }

    @Override
    public boolean equals (Object that)
    {
        if (! (that instanceof MatrixBoolean)) return false;
        MatrixBoolean B = (MatrixBoolean) that;

        int columnCount = Math.min (data.size (), B.data.size ());
        int c = 0;
        for (; c < columnCount; c++)
        {
            boolean[] columnA =   data.get (c);
            boolean[] columnB = B.data.get (c);
            int rowCount = Math.min (columnA.length, columnB.length);
            int r = 0;
            for (; r < rowCount; r++) if (columnA[r] != columnB[r]) return false;
            // The following two loops are mutually exclusive.
            for (; r < columnA.length; r++) if (columnA[r]) return false;
            for (; r < columnB.length; r++) if (columnB[r]) return false;
        }
        for (; c < data.size (); c++)
        {
            boolean[] columnA = data.get (c);
            for (int r = 0; r < columnA.length; r++) if (columnA[r]) return false;
        }
        for (; c < B.data.size (); c++)
        {
            boolean[] columnB = B.data.get (c);
            for (int r = 0; r < columnB.length; r++) if (columnB[r]) return false;
        }
        return true;
    }

    @Override
    public String toString ()
    {
        StringBuilder result = new StringBuilder ();
        for (int r = 0; r < rowCount; r++)
        {
            for (int c = 0; c < data.size (); c++)
            {
                boolean[] column = data.get (c);
                if (column.length <= r)
                {
                    result.append ("0");
                }
                else
                {
                    if (column[r]) result.append ("1");
                    else           result.append ("0");
                }
            }
            result.append ("\n");
        }
        return result.toString ();
    }
}
