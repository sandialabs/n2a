/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

/**
    Matrix type.
    Many of the functions here are adapted from FL Matrix.
**/
public class MatrixDense extends Matrix
{
    protected double[][] value;  // stored in column-major order; that is, an access to A(r,c) is fulfilled as value[c][r]

    public MatrixDense ()
    {
    }

    public MatrixDense (int rows, int columns)
    {
        value = new double[columns][rows];
    }

    public MatrixDense (int rows, int columns, double initialValue)
    {
        value = new double[columns][rows];
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                value[c][r] = initialValue;
            }
        }
    }

    public MatrixDense (Matrix A)
    {
        int columns = A.columns ();
        int rows    = A.rows ();
        value = new double[columns][rows];
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                value[c][r] = A.get (r, c);
            }
        }
    }

    public MatrixDense (BufferedReader reader) throws EvaluationException
    {
        load (reader);
    }

    public MatrixDense (Text that) throws EvaluationException
    {
        load (new StringReader (that.value));
    }

    public MatrixDense (String that) throws EvaluationException
    {
        load (new StringReader (that));
    }

    public MatrixDense (String that, boolean units) throws EvaluationException
    {
        load (new StringReader (that), units);
    }

    public void load (Reader stream) throws EvaluationException
    {
        load (stream, false);
    }

    public void load (Reader stream, boolean units) throws EvaluationException
    {
        try
        {
            ArrayList<ArrayList<Double>> temp = new ArrayList<ArrayList<Double>> ();
            ArrayList<Double> row = new ArrayList<Double> ();
            int columns = 0;
            boolean transpose = false;

            // Scan for opening "["
            char token;
            do
            {
                token = (char) stream.read ();
                if (token == '~') transpose = true;
            }
            while (token != '['  &&  stream.ready ());

            // Read rows until closing "]"
            char[] buffer = new char[1024];  // for one floating-point number, so far more than enough
            int index = 0;
            boolean done = false;
            while (stream.ready ()  &&  ! done)
            {
                token = (char) stream.read ();
                switch (token)
                {
                    case '\r':
                        break;  // ignore CR characters
                    case ' ':
                    case '\t':
                        if (index == 0) break;  // ignore leading whitespace (equivalent to trim)
                    case ',':
                        // Process element
                        if (index == 0)
                        {
                            row.add (0.0);
                        }
                        else
                        {
                            String value = String.valueOf (buffer, 0, index);
                            index = 0;
                            if (units) row.add (Scalar.convert (value));
                            else       row.add (Double.valueOf (value));
                        }
                        break;
                    case ']':
                        done = true;
                    case ';':
                    case '\n':
                        // Process any final element
                        if (index > 0)
                        {
                            String value = String.valueOf (buffer, 0, index);
                            index = 0;
                            if (units) row.add (Scalar.convert (value));
                            else       row.add (Double.valueOf (value));
                        }
                        // Process line
                        int c = row.size ();
                        if (c > 0)
                        {
                            temp.add (row);
                            columns = Math.max (columns, c);
                            row = new ArrayList<Double> (columns);
                        }
                        break;
                    default:
                        buffer[index++] = token;  // If we overrun the buffer, we should automatically get an index out of range error.
                }
            }

            // Assign elements to "value"
            int rows = temp.size ();
            if (transpose)
            {
                value = new double[rows][columns];
                for (int r = 0; r < rows; r++)
                {
                    row = temp.get (r);
                    for (int c = 0; c < row.size (); c++)
                    {
                        value[r][c] = row.get (c);
                    }
                }
            }
            else
            {
                value = new double[columns][rows];
                for (int r = 0; r < rows; r++)
                {
                    row = temp.get (r);
                    for (int c = 0; c < row.size (); c++)
                    {
                        value[c][r] = row.get (c);
                    }
                }
            }

            if (value.length == 0  ||  value[0].length == 0) throw new EvaluationException ("Empty matrix");
        }
        catch (IOException error)
        {
            throw new EvaluationException ("Failed to convert input to matrix");
        }
    }

    public int rows ()
    {
        if (value.length < 1) return 0;
        return value[0].length;
    }

    public int columns ()
    {
        return value.length;
    }

    public double get (int row, int column)
    {
        return value[column][row];
    }

    public double get (int row)
    {
        return value[0][row];
    }

    public double[] getRawColumn (int column)
    {
        return value[column];
    }

    public void set (int row, int column, double a)
    {
        value[column][row] = a;
    }

    public void set (int row, double a)
    {
        value[0][row] = a;
    }

    public Type clear ()
    {
        int w = value.length;
        if (w < 1) return new MatrixDense ();
        int h = value[0].length;
        if (h < 1) return new MatrixDense ();
        return new MatrixDense (h, w);
    }

    public MatrixDense clear (double initialValue)
    {
        int w = value.length;
        if (w < 1) return new MatrixDense ();
        int h = value[0].length;
        if (h < 1) return new MatrixDense ();
        return new MatrixDense (h, w, initialValue);
    }

    /**
        @return A copy of this object, with diagonal elements set to 1 and off-diagonals set to zero.
     */
    public MatrixDense identity ()
    {
        int w = value.length;
        if (w < 1) return new MatrixDense ();
        int h = value[0].length;
        if (h < 1) return new MatrixDense ();

        MatrixDense result = new MatrixDense (h, w);
        h = Math.min (w, h);
        for (int r = 0; r < h; r++) result.value[r][r] = 1;
        return result;
    }

    public Type add (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] + B[c][r];
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.columns ());
            int oh = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] + B.get (r, c);
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] + scalar;
                }
            }
            return result;
        }
        if (that instanceof Text) return new Text (toString ()).add (that);
        throw new EvaluationException ("type mismatch");
    }

    public Type subtract (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] - B[c][r];
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.columns ());
            int oh = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] - B.get (r, c);
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] - scalar;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type multiply (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int h = value[0].length;
            int w = B.length;
            int m = Math.min (value.length, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    double sum = 0;
                    for (int j = 0; j < m; j++)
                    {
                        sum += value[j][r] * B[c][j];
                    }
                    result.value[c][r] = sum;
                }
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int h = value[0].length;
            int w = B.columns ();
            int m = Math.min (value.length, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    double sum = 0;
                    for (int j = 0; j < m; j++)
                    {
                        sum += value[j][r] * B.get (j, c);
                    }
                    result.value[c][r] = sum;
                }
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] * scalar;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type multiplyElementwise (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] * B[c][r];
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.columns ());
            int oh = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] * B.get (r, c);
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] * scalar;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type divide (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] / B[c][r];
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.columns ());
            int oh = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = value[c][r] / B.get (r, c);
              for (int r = oh; r < h;  r++) result.value[c][r] = value[c][r];
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = value[c][r];
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] / scalar;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type min (Type that)
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = Math.min (value[c][r], B[c][r]);
              for (int r = oh; r < h;  r++) result.value[c][r] = Math.min (value[c][r], 0);
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = Math.min (value[c][r], 0);
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.columns ());
            int oh = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = Math.min (value[c][r], B.get (r, c));
              for (int r = oh; r < h;  r++) result.value[c][r] = Math.min (value[c][r], 0);
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = Math.min (value[c][r], 0);
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.min (value[c][r], scalar);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type max (Type that)
    {
        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = Math.max (value[c][r], B[c][r]);
              for (int r = oh; r < h;  r++) result.value[c][r] = Math.max (value[c][r], 0);
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = Math.max (value[c][r], 0);
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.columns ());
            int oh = Math.min (h, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < ow; c++)
            {
              for (int r = 0;  r < oh; r++) result.value[c][r] = Math.max (value[c][r], B.get (r, c));
              for (int r = oh; r < h;  r++) result.value[c][r] = Math.max (value[c][r], 0);
            }
            for (int c = ow; c < w; c++)
            {
              for (int r = 0; r < h; r++)   result.value[c][r] = Math.max (value[c][r], 0);
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.max (value[c][r], scalar);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type negate () throws EvaluationException
    {
        int w = value.length;
        int h = value[0].length;
        MatrixDense result = new MatrixDense (h, w);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.value[c][r] = -value[c][r];
            }
        }
        return result;
    }

    public Type transpose ()
    {
        int w = value.length;
        int h = value[0].length;
        MatrixDense result = new MatrixDense (w, h);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.value[r][c] = value[c][r];
            }
        }
        return result;
    }

    public Type visit (Visitor visitor)
    {
        int w = value.length;
        int h = value[0].length;
        MatrixDense result = new MatrixDense (h, w);
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result.value[c][r] = visitor.apply (value[c][r]);
            }
        }
        return result;
    }

    public double determinant () throws EvaluationException
    {
        int w = value.length;
        int h = value[0].length;
        if (h != w) throw new EvaluationException ("Can't compute determinant of non-square matrix.");
        if (h == 1) return value[0][0];
        if (h == 2) return value[0][0] * value[1][1] - value[0][1] * value[1][0];
        if (h == 3)
        {
            return   value[0][0] * value[1][1] * value[2][2]
                   - value[0][0] * value[2][1] * value[1][2]
                   - value[1][0] * value[0][1] * value[2][2]
                   + value[1][0] * value[2][1] * value[0][2]
                   + value[2][0] * value[0][1] * value[1][2]
                   - value[2][0] * value[1][1] * value[0][2];
        }
        throw new EvaluationException ("Can't compute deteminant of matrices larger then 3x3 (because we are lazy).");
    }

    public double norm (double n)
    {
        int w = value.length;
        int h = value[0].length;
        double result = 0;
        if (n == 0)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    if (value[c][r] != 0) result++;
                }
            }
        }
        else if (n == 1)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result += Math.abs (value[c][r]);
                }
            }
        }
        else if (n == 2)
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result += value[c][r] * value[c][r];
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
                    result = Math.max (result, Math.abs (value[c][r]));
                }
            }
        }
        else
        {
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result += Math.pow (value[c][r], n);
                }
            }
            result = Math.pow (result, 1 / n);
        }
        return result;
    }

    public int compareTo (Type that)
    {
        int cols = value.length;
        int rows = 0;
        if (cols != 0) rows = value[0].length;

        if (that instanceof MatrixDense)
        {
            double[][] B = ((MatrixDense) that).value;
            int Bcols = value.length;
            int difference = cols - Bcols;
            if (difference != 0) return difference;
            int Brows = 0;
            if (Bcols != 0) Brows = B[0].length;
            difference = rows - Brows;
            if (difference != 0) return difference;

            for (int c = 0; c < cols; c++)
            {
                for (int r = 0; r < rows; r++)
                {
                    double d = value[c][r] - B[c][r];
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
            double d = value[0][0] - ((Scalar) that).value;
            if (d > 0) return 1;
            if (d < 0) return -1;
            return 0;
        }
        throw new EvaluationException ("type mismatch");
    }
}
