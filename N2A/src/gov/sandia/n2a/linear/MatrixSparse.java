/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.linear;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class MatrixSparse extends Matrix
{
    List<HashMap<Integer,Double>> data = new ArrayList<HashMap<Integer,Double>> ();
    int    rowCount;  // Largest index seen in any column.
    double emptyValue;

    public MatrixSparse ()
    {
    }

    public MatrixSparse (int rows, int columns)
    {
        rowCount = rows;
        for (int c = 0; c < columns; c++) data.add (null);
    }

    public MatrixSparse (int rows, int columns, double initialValue)
    {
        rowCount   = rows;
        emptyValue = initialValue;
        for (int c = 0; c < columns; c++) data.add (null);
    }

    public MatrixSparse (Matrix A)
    {
        int columns = A.columns ();
        int rows    = A.rows ();
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                set (r, c, A.get (r, c));
            }
        }
    }

    public MatrixSparse (BufferedReader reader)
    {
        load (reader);
    }

    public MatrixSparse (Text that) throws EvaluationException
    {
        load (new BufferedReader (new StringReader (that.value)));
    }

    public MatrixSparse (String that) throws EvaluationException
    {
        load (new BufferedReader (new StringReader (that)));
    }

    public MatrixSparse (String that, boolean units) throws EvaluationException
    {
        load (new BufferedReader (new StringReader (that)), units);
    }

    public void load (BufferedReader reader) throws EvaluationException
    {
        load (reader, false);
    }

    public void load (BufferedReader reader, boolean units) throws EvaluationException
    {
        try
        {
            String line = reader.readLine ();  // Throw away "Sparse" line
            while (true)
            {
                line = reader.readLine ();
                if (line == null) break;
                line = line.trim ();
                String[] pieces = line.split (",");
                if (pieces.length < 3) continue;
                int    r = Integer.valueOf (pieces[0].trim ());
                int    c = Integer.valueOf (pieces[1].trim ());
                double v = Double .valueOf (pieces[2].trim ());
                set (r, c, v);
            }
        }
        catch (IOException error)
        {
            throw new EvaluationException ("Failed to convert input to matrix");
        }
    }

    public int rows ()
    {
        return rowCount;
    }

    public int columns ()
    {
        return data.size ();
    }

    public double get (int row, int column)
    {
        if (column >= data.size ()) return 0;
        HashMap<Integer,Double> rows = data.get (column);
        if (rows == null) return 0;
        Double result = rows.get (row);
        if (result == null) return 0;
        return result;
    }

    public void set (int row, int column, double a)
    {
        for (int c = data.size (); c <= column; c++) data.add (null);
        HashMap<Integer,Double> rows = data.get (column);
        if (rows == null)
        {
            rows = new HashMap<Integer,Double> ();
            data.set (column, rows);
        }
        if (a == emptyValue)
        {
            rows.remove (row);
        }
        else
        {
            rows.put (row, a);
            rowCount = Math.max (rowCount, row + 1);
        }
    }

    public MatrixSparse clear (double initialValue)
    {
        return new MatrixSparse (rows (), columns (), initialValue);
    }

    /**
        @return A copy of this object, with diagonal elements set to 1 and off-diagonals set to zero.
     */
    public MatrixSparse identity ()
    {
        int w = columns ();
        int h = rows ();
        MatrixSparse result = new MatrixSparse (h, w);
        h = Math.min (h, w);
        for (int r = 0; r < h; r++) result.set (r, r, 1);
        return result;
    }

    public boolean isZero ()
    {
        for (HashMap<Integer,Double> c : data)
        {
            for (Double d : c.values ())
            {
                if (d != 0) return false;
            }
        }
        return true;
    }

    public Matrix add (Matrix that) throws EvaluationException
    {
        if (that instanceof MatrixSparse)
        {
            MatrixSparse B = (MatrixSparse) that;
            int w  = columns ();
            int h  = rows ();
            int Bw = B.columns ();
            MatrixSparse result = new MatrixSparse (h, w);
            for (int c = 0; c < w; c++)
            {
                HashMap<Integer,Double> rows = data.get (c);
                if (rows == null) continue;
                for (Entry<Integer,Double> row : rows.entrySet ()) result.set (row.getKey (), c, row.getValue ());
            }
            for (int c = 0; c < Bw; c++)
            {
                HashMap<Integer,Double> rows = B.data.get (c);
                if (rows == null) continue;
                for (Entry<Integer,Double> row : rows.entrySet ())
                {
                    int r = row.getKey ();
                    result.set (r, c, result.get (r, c) + row.getValue ());
                }
            }
            return result;
        }

        Matrix B = (Matrix) that;
        int w = columns ();
        int h = rows ();
        int ow = Math.min (w, B.columns ());
        int oh = Math.min (h, B.rows ());
        MatrixDense result;
        if (emptyValue == 0) result = new MatrixDense (h, w);
        else                 result = new MatrixDense (h, w, emptyValue);
        for (int c = 0; c < w; c++)
        {
            HashMap<Integer,Double> rows = data.get (c);
            if (rows == null) continue;
            for (Entry<Integer,Double> row : rows.entrySet ()) result.set (row.getKey (), c, row.getValue ());
        }
        for (int c = 0; c < ow; c++)
        {
          for (int r = 0; r < oh; r++) result.set (r, c, result.get (r, c) + B.get (r, c));
        }
        return result;
    }

    public MatrixSparse add (Scalar that) throws EvaluationException
    {
        double scalar = ((Scalar) that).value;
        int w = columns ();
        int h = rows ();
        MatrixSparse result = new MatrixSparse (h, w, emptyValue + scalar);
        for (int c = 0; c < w; c++)
        {
            HashMap<Integer,Double> rows = data.get (c);
            if (rows == null) continue;
            for (Entry<Integer,Double> row : rows.entrySet ()) result.set (row.getKey (), c, row.getValue () + scalar);
        }
        return result;
    }

    // TODO: Fill in other binary operations

    public MatrixSparse negate () throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        MatrixSparse result = new MatrixSparse (h, w, -emptyValue);
        for (int c = 0; c < w; c++)
        {
            HashMap<Integer,Double> rows = data.get (c);
            if (rows == null) continue;
            for (Entry<Integer,Double> row : rows.entrySet ()) result.set (row.getKey (), c, -row.getValue ());
        }
        return result;
    }

    public MatrixSparse transpose ()
    {
        int w = columns ();
        int h = rows ();
        MatrixSparse result = new MatrixSparse (w, h, emptyValue);
        for (int c = 0; c < w; c++)
        {
            HashMap<Integer,Double> rows = data.get (c);
            if (rows == null) continue;
            for (Entry<Integer,Double> row : rows.entrySet ()) result.set (c, row.getKey (), row.getValue ());
        }
        return result;
    }

    public MatrixSparse visit (Visitor visitor)
    {
        int w = columns ();
        int h = rows ();
        MatrixSparse result = new MatrixSparse (h, w, visitor.apply (emptyValue));
        for (int c = 0; c < w; c++)
        {
            HashMap<Integer,Double> rows = data.get (c);
            if (rows == null) continue;
            for (Entry<Integer,Double> row : rows.entrySet ()) result.set (row.getKey (), c, visitor.apply (row.getValue ()));
        }
        return result;
    }

    public double norm (double n)
    {
        int w = columns ();
        int h = rows ();
        double result = 0;
        int emptyCount = w * h;
        if (n == 0)
        {
            for (int c = 0; c < w; c++)
            {
                HashMap<Integer,Double> rows = data.get (c);
                if (rows == null) continue;
                emptyCount -= rows.size ();
                for (Double v : rows.values ()) if (v != 0) result++;
            }
            result += emptyCount * emptyValue;
        }
        else if (n == 1)
        {
            for (int c = 0; c < w; c++)
            {
                HashMap<Integer,Double> rows = data.get (c);
                if (rows == null) continue;
                emptyCount -= rows.size ();
                for (Double v : rows.values ()) result += Math.abs (v);
            }
            result += emptyCount * Math.abs (emptyValue);
        }
        else if (n == 2)
        {
            for (int c = 0; c < w; c++)
            {
                HashMap<Integer,Double> rows = data.get (c);
                if (rows == null) continue;
                emptyCount -= rows.size ();
                for (Double v : rows.values ()) result += v * v;
            }
            result += emptyCount * emptyValue * emptyValue;
            result = Math.sqrt (result);
        }
        else if (n == Double.POSITIVE_INFINITY)
        {
            for (int c = 0; c < w; c++)
            {
                HashMap<Integer,Double> rows = data.get (c);
                if (rows == null) continue;
                for (Double v : rows.values ()) result = Math.max (result, Math.abs (v));
            }
            result = Math.max (result, Math.abs (emptyValue));
        }
        else
        {
            for (int c = 0; c < w; c++)
            {
                HashMap<Integer,Double> rows = data.get (c);
                if (rows == null) continue;
                emptyCount -= rows.size ();
                for (Double v : rows.values ()) result += Math.pow (v, n);
            }
            result += emptyCount * Math.pow (emptyValue, n);
            result = Math.pow (result, 1 / n);
        }
        return result;
    }

    public static class IteratorSparse implements IteratorNonzero
    {
        protected MatrixSparse                    A;
        protected int                             columns;

        protected Iterator<Entry<Integer,Double>> it;
        protected double                          value;
        protected int                             row;
        protected int                             column;

        public IteratorSparse (MatrixSparse A)
        {
            this.A = A;
            columns = A.columns ();
            if (columns > 0)
            {
                HashMap<Integer,Double> rows = A.data.get (0);
                if (rows != null) it = rows.entrySet ().iterator ();
            }
        }

        public boolean hasNext ()
        {
            while (true)
            {
                if (it != null  &&  it.hasNext ()) return true;
                if (++column >= columns) return false;
                HashMap<Integer,Double> rows = A.data.get (column);
                if (rows == null) it = null;
                else              it = rows.entrySet ().iterator ();
            }
        }

        public Double next ()
        {
            if (! hasNext ()) return null;
            Entry<Integer,Double> e = it.next ();
            row = e.getKey ();
            value = e.getValue ();
            return value;
        }

        public int getRow ()
        {
            return row;
        }

        public int getColumn ()
        {
            return column;
        }
    }

    public IteratorNonzero getIteratorNonzero ()
    {
        return new IteratorSparse (this);
    }
}
