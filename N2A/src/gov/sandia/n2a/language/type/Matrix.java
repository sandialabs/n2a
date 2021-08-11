/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import gov.sandia.n2a.backend.internal.Holder;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

public abstract class Matrix extends Type implements Holder
{
    public interface Visitor
    {
        double apply (double a);
    }

    public static Matrix factory (Path path) throws EvaluationException
    {
        try (BufferedReader reader = Files.newBufferedReader (path))
        {
            char buffer[] = new char[10];
            reader.mark (buffer.length + 1);
            reader.read (buffer);  // just assume buffer is filled completely
            String line = new String (buffer);
            reader.reset ();

            if (line.toLowerCase ().startsWith ("sparse")) return new MatrixSparse (reader);
            // Could do further triage on file format, and call various appropriate versions of MatrixDense.load directly.
            // TODO: import Matlab format.
            return new MatrixDense (reader);
        }
        catch (IOException exception)
        {
            throw new EvaluationException ("Can't open matrix file");
        }
    }

    public void close ()
    {
        // Don't do anything, since we load the entire matrix into memory, so have already closed the file.
    }

    public abstract int rows ();

    public abstract int columns ();

    public abstract double get (int row, int column);

    public double get (int row)
    {
        return get (row, 0);
    }

    public double get (double row, double column, boolean raw)
    {
        int rows    = rows ();
        int columns = columns ();
        int lastRow    = rows    - 1;
        int lastColumn = columns - 1;

        if (raw)
        {
            int r = (int) Math.floor (row);
            int c = (int) Math.floor (column);
            if      (r < 0    ) r = 0;
            else if (r >= rows) r = lastRow;
            if      (c < 0       ) c = 0;
            else if (c >= columns) c = lastColumn;
            return get (r, c);
        }
        else
        {
            row    *= lastRow;
            column *= lastColumn;
            int r = (int) Math.floor (row);
            int c = (int) Math.floor (column);
            if (r < 0)
            {
                if      (c <  0         ) return get (0, 0);
                else if (c >= lastColumn) return get (0, lastColumn);
                else
                {
                    double b = column - c;
                    return (1 - b) * get (0, c) + b * get (0, c+1);
                }
            }
            else if (r >= lastRow)
            {
                if      (c <  0         ) return get (lastRow, 0);
                else if (c >= lastColumn) return get (lastRow, lastColumn);
                else
                {
                    double b = column - c;
                    return (1 - b) * get (lastRow, c) + b * get (lastRow, c+1);
                }
            }
            else
            {
                double a = row - r;
                double a1 = 1 - a;
                if      (c <  0         ) return a1 * get (r, 0         ) + a * get (r+1, 0         );
                else if (c >= lastColumn) return a1 * get (r, lastColumn) + a * get (r+1, lastColumn);
                else
                {
                    double b = column - c;
                    return   (1 - b) * (a1 * get (r, c  ) + a * get (r+1, c  ))
                           +      b  * (a1 * get (r, c+1) + a * get (r+1, c+1));
                }
            }
        }
    }

    public abstract void set (int row, int column, double a);

    public void set (int row, double a)
    {
        set (row, 0, a);
    }

    public Type clear ()
    {
        return clear (0);
    }

    public abstract Matrix clear (double initialValue);

    /**
        @return copy of this object with diagonal elements set to 1 and off-diagonals set to zero.
    **/
    public abstract Matrix identity ();

    public boolean isZero ()
    {
        return new MatrixDense (this).isZero ();
    }

    /**
        Unlike most other operations on Matrix, this one could possibly return a Text object,
        if the user is concatenating a matrix with a string.
    **/
    public Type add (Type that) throws EvaluationException
    {
        return new MatrixDense (this).add (that);
    }

    public Matrix subtract (Type that) throws EvaluationException
    {
        return new MatrixDense (this).subtract (that);
    }

    public Matrix multiply (Type that) throws EvaluationException
    {
        return new MatrixDense (this).multiply (that);
    }

    public Matrix multiplyElementwise (Type that) throws EvaluationException
    {
        return new MatrixDense (this).multiplyElementwise (that);
    }

    public Matrix divide (Type that) throws EvaluationException
    {
        return new MatrixDense (this).divide (that);
    }

    public Matrix min (Type that)
    {
        return new MatrixDense (this).min (that);
    }

    public Matrix max (Type that)
    {
        return new MatrixDense (this).max (that);
    }

    public Type EQ (Type that) throws EvaluationException
    {
        if (that instanceof Matrix) return new Scalar (compareTo (that) == 0 ? 1 : 0);
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            int w = columns ();
            int h = rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) == b) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type NE (Type that) throws EvaluationException
    {
        if (that instanceof Matrix) return new Scalar (compareTo (that) != 0 ? 1 : 0);
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            int w = columns ();
            int h = rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) != b) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Matrix GT (Type that) throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) > b) ? 1 : 0);
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            w = Math.min (w, B.columns ());
            h = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) > B.get (r, c)) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Matrix GE (Type that) throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) >= b) ? 1 : 0);
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            w = Math.min (w, B.columns ());
            h = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) >= B.get (r, c)) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Matrix LT (Type that) throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) < b) ? 1 : 0);
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            w = Math.min (w, B.columns ());
            h = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) < B.get (r, c)) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Matrix LE (Type that) throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) <= b) ? 1 : 0);
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            w = Math.min (w, B.columns ());
            h = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) <= B.get (r, c)) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Matrix AND (Type that) throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (that instanceof Scalar)
        {
            double b = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) * b != 0) ? 1 : 0);
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            w = Math.min (w, B.columns ());
            h = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (get (r, c) * B.get (r, c) != 0) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Matrix OR (Type that) throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (that instanceof Scalar)
        {
            double b = Math.abs (((Scalar) that).value);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (Math.abs (get (r, c)) + b != 0) ? 1 : 0);
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            w = Math.min (w, B.columns ());
            h = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.set (r, c, (Math.abs (get (r, c)) + Math.abs (B.get (r, c)) != 0) ? 1 : 0);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public double det22 (int r0, int r1, int c0, int c1)
    {
        return get (r0, c0) * get (r1, c1) - get (r0, c1) * get (r1, c0);
    }

    public Type NOT () throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (h != w) throw new EvaluationException ("Can't invert non-square matrix (because we don't know about pseudo-inverse).");  // should create pseudo-inverse
        if (h == 1) return new Scalar (1 / get (0, 0));
        if (h == 2)
        {
            MatrixDense result = new MatrixDense (2, 2);

            double q = determinant ();
            if (q == 0) throw new EvaluationException ("invert: Matrix is singular!");

            result.set (0, 0, get (1, 1) /  q);
            result.set (1, 0, get (1, 0) / -q);
            result.set (0, 1, get (0, 1) / -q);
            result.set (1, 1, get (0, 0) /  q);

            return result;
        }
        if (h == 3)
        {
            MatrixDense result = new MatrixDense (3, 3);

            double q = determinant ();
            if (q == 0) throw new EvaluationException ("invert: Matrix is singular!");

            result.set (0, 0, det22 (1, 2, 1, 2) / q);
            result.set (1, 0, det22 (1, 2, 2, 0) / q);
            result.set (2, 0, det22 (1, 2, 0, 1) / q);
            result.set (0, 1, det22 (0, 2, 2, 1) / q);
            result.set (1, 1, det22 (0, 2, 0, 2) / q);
            result.set (2, 1, det22 (0, 2, 1, 0) / q);
            result.set (0, 2, det22 (0, 1, 1, 2) / q);
            result.set (1, 2, det22 (0, 1, 2, 0) / q);
            result.set (2, 2, det22 (0, 1, 0, 1) / q);

            return result;
        }
        throw new EvaluationException ("Can't invert matrices larger then 3x3 (because we are not using a good numerical library).");
    }

    public Matrix negate () throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        MatrixDense result = new MatrixDense (h, w);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.set (r, c, -get (r, c));
            }
        }
        return result;
    }

    public Matrix transpose ()
    {
        return new MatrixDense (this).transpose ();
    }

    public Matrix visit (Visitor visitor)
    {
        int w = columns ();
        int h = rows ();
        MatrixDense result = new MatrixDense (h, w);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.set (r, c, visitor.apply (get (r, c)));
            }
        }
        return result;
    }

    public double determinant () throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        if (h != w) throw new EvaluationException ("Can't compute determinant of non-square matrix.");
        if (h == 1) return get (0, 0);
        if (h == 2) return get (0, 0) * get (1, 1) - get (1, 0) * get (0, 1);
        if (h == 3)
        {
            return   get (0, 0) * get (1, 1) * get (2, 2)
                   - get (0, 0) * get (1, 2) * get (2, 1)
                   - get (0, 1) * get (1, 0) * get (2, 2)
                   + get (0, 1) * get (1, 2) * get (2, 0)
                   + get (0, 2) * get (1, 0) * get (2, 1)
                   - get (0, 2) * get (1, 1) * get (2, 0);
        }
        throw new EvaluationException ("Can't compute deteminant of matrices larger then 3x3 (because we are lazy).");
    }

    public double norm (double n)
    {
        int w = columns ();
        int h = rows ();
        double result = 0;
        if (n == 0)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    if (get (r, c) != 0) result++;
                }
            }
        }
        else if (n == 1)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result += Math.abs (get (r, c));
                }
            }
        }
        else if (n == 2)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    double v = get (r, c);
                    result += v * v;
                }
            }
            result = Math.sqrt (result);
        }
        else if (n == Double.POSITIVE_INFINITY)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result = Math.max (result, Math.abs (get (r, c)));
                }
            }
        }
        else
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result += Math.pow (get (r, c), n);
                }
            }
            result = Math.pow (result, 1 / n);
        }
        return result;
    }

    public double sumSquares ()
    {
        int w = columns ();
        int h = rows ();
        double result = 0;
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                double v = get (r, c);
                result += v * v;
            }
        }
        return result;
    }

    public boolean betterThan (Type that)
    {
        if (that instanceof Matrix  ) return false;
        if (that instanceof Text    ) return false;
        if (that instanceof Instance) return false;
        return true;
    }

    public String toString ()
    {
        return print (";", ",");
    }

    public String print ()
    {
        return print ("\n", "\t");
    }

    public String print (String rowDivider, String columnDivider)
    {
        StringWriter stream = new StringWriter ();

        int columns = columns ();
        if (columns == 0) return "[]";
        int rows    = rows ();
        if (rows    == 0) return "[]";

        stream.append ("[");
        int r = 0;
        while (true)
        {
            int c = 0;
            while (true)
            {
                stream.append (String.valueOf (get (r, c)));
                if (++c >= columns) break;
                stream.append (columnDivider);
            }

            if (++r >= rows) break;
            stream.append (rowDivider);
        }
        stream.append ("]");

        return stream.toString ();
    }

    public int compareTo (Type that)
    {
        int cols = columns ();
        int rows = rows ();

        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int Bcols = B.columns ();
            int difference = cols - Bcols;
            if (difference != 0) return difference;
            int Brows = B.rows ();
            difference = rows - Brows;
            if (difference != 0) return difference;

            for (int c = 0; c < cols; c++)
            {
                for (int r = 0; r < rows; r++)
                {
                    double d = get (r, c) - B.get (r, c);
                    if (d > 0) return 1;
                    if (d < 0) return -1;
                }
            }
            return 0;
        }
        if (that instanceof Scalar)
        {
            if (cols != 1) return 1;
            if (rows != 1) return 1;
            double d = get (0, 0) - ((Scalar) that).value;
            if (d > 0) return 1;
            if (d < 0) return -1;
            return 0;
        }
        throw new EvaluationException ("type mismatch");
    }

    /**
        Unlike the usual Iterator interface, we promise that next() will return null
        when all elements are consumed, rather than throw an exception.
    **/
    public static interface IteratorNonzero extends Iterator<Double>
    {
        public int getRow ();
        public int getColumn ();
    }

    /**
        Returns non-zero elements in column-major order, while skipping all zero elements.
    **/
    public static class IteratorSkip implements IteratorNonzero
    {
        protected Matrix A;
        protected int rows; // cached value from A
        protected int columns;

        protected double value;
        protected int    row = -1; // of value
        protected int    column;

        protected double nextValue;
        protected int    nextRow = -1;
        protected int    nextColumn;

        public IteratorSkip (Matrix A)
        {
            this.A = A;
            rows = A.rows ();
            columns = A.columns ();
            getNext ();
        }

        public void getNext ()
        {
            for (; nextColumn < columns; nextColumn++)
            {
                while (true)
                {
                    if (++nextRow >= rows) break;
                    nextValue = A.get (nextRow, nextColumn);
                    if (nextValue != 0) return;
                }
                nextRow = -1;
            }
        }

        public boolean hasNext ()
        {
            return nextRow >= 0;
        }

        public Double next ()
        {
            if (nextRow < 0) return null;
            value  = nextValue;
            row    = nextRow;
            column = nextColumn;
            getNext ();
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
        return new IteratorSkip (this);
    }
}
