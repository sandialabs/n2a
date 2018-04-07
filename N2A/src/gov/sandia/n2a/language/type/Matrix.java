/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.type;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

public abstract class Matrix extends Type
{
    public interface Visitor
    {
        double apply (double a);
    }

    public static Matrix factory (File path) throws EvaluationException
    {
        try (BufferedReader reader = new BufferedReader (new InputStreamReader ((new FileInputStream (path)))))
        {
            String line = reader.readLine ();
            if (line.toLowerCase ().contains ("sparse")) return new MatrixSparse (path);
            // Could do further triage on file format, and call various appropriate versions of MatrixDense.load directly.
            // TODO: import Matlab format.
            return new MatrixDense (path);
        }
        catch (IOException exception)
        {
            throw new EvaluationException ("Can't open matrix file");
        }
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
        @return A copy of this object, with diagonal elements set to 1 and off-diagonals set to zero.
    **/
    public abstract Matrix identity ();

    public Type add (Type that) throws EvaluationException
    {
        return new MatrixDense (this).add (that);
    }

    public Type subtract (Type that) throws EvaluationException
    {
        return new MatrixDense (this).subtract (that);
    }

    public Type multiply (Type that) throws EvaluationException
    {
        return new MatrixDense (this).multiply (that);
    }

    public Type multiplyElementwise (Type that) throws EvaluationException
    {
        return new MatrixDense (this).multiplyElementwise (that);
    }

    public Type divide (Type that) throws EvaluationException
    {
        return new MatrixDense (this).divide (that);
    }

    public Type min (Type that)
    {
        return new MatrixDense (this).min (that);
    }

    public Type max (Type that)
    {
        return new MatrixDense (this).max (that);
    }

    public Type EQ (Type that) throws EvaluationException
    {
        return new Scalar (compareTo (that) == 0 ? 1 : 0);
    }

    public Type NE (Type that) throws EvaluationException
    {
        return new Scalar (compareTo (that) == 0 ? 0 : 1);
    }

    public double det22 (int r0, int r1, int c0, int c1)
    {
        return (get (r0, c0) * get (r1, c1) - get (r0, c1) * get (r1, c0));
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

            result.value[0][0] = get (1, 1) /  q;
            result.value[0][1] = get (1, 0) / -q;
            result.value[1][0] = get (0, 1) / -q;
            result.value[1][1] = get (0, 0) /  q;

            return result;
        }
        if (h == 3)
        {
            MatrixDense result = new MatrixDense (3, 3);

            double q = determinant ();
            if (q == 0) throw new EvaluationException ("invert: Matrix is singular!");

            result.value[0][0] = det22 (1, 2, 1, 2) / q;
            result.value[0][1] = det22 (1, 2, 2, 0) / q;
            result.value[0][2] = det22 (1, 2, 0, 1) / q;
            result.value[1][0] = det22 (0, 2, 2, 1) / q;
            result.value[1][1] = det22 (0, 2, 0, 2) / q;
            result.value[1][2] = det22 (0, 2, 1, 0) / q;
            result.value[2][0] = det22 (0, 1, 1, 2) / q;
            result.value[2][1] = det22 (0, 1, 2, 0) / q;
            result.value[2][2] = det22 (0, 1, 0, 1) / q;

            return result;
        }
        throw new EvaluationException ("Can't invert matrices larger then 3x3 (because we are not using a good numerical library).");
    }

    public Type negate () throws EvaluationException
    {
        int w = columns ();
        int h = rows ();
        MatrixDense result = new MatrixDense (h, w);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.value[c][r] = -get (r, c);
            }
        }
        return result;
    }

    public Type transpose ()
    {
        int w = columns ();
        int h = rows ();
        MatrixDense result = new MatrixDense (w, h);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.value[r][c] = get (r, c);
            }
        }
        return result;
    }

    public Type visit (Visitor visitor)
    {
        int w = columns ();
        int h = rows ();
        MatrixDense result = new MatrixDense (h, w);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.value[c][r] = visitor.apply (get (r, c));
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

    public boolean betterThan (Type that)
    {
        if (that instanceof Matrix  ) return false;
        if (that instanceof Text    ) return false;
        if (that instanceof Instance) return false;
        return true;
    }

    public String toString ()
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
                stream.append (',');
            }

            if (++r >= rows) break;
            stream.append (";");
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
}
