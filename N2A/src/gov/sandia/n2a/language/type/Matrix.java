/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

/**
    Matrix type.
    Many of the functions here are adapted from FL Matrix.
**/
public class Matrix extends Type
{
    public double[][] value;  // stored in column-major order; that is, an access to A(r,c) is fulfilled as value[c][r]

    public interface Visitor
    {
        double apply (double a);
    }

    public Matrix ()
    {
    }

    public Matrix (int rows, int columns)
    {
        value = new double[columns][rows];
    }

    public Matrix (File path) throws EvaluationException
    {
        try
        {
            load (new InputStreamReader (new FileInputStream (path)));
        }
        catch (IOException exception)
        {
            throw new EvaluationException ("Can't open matrix file");
        }
    }

    public Matrix (Text that) throws EvaluationException
    {
        load (new StringReader (that.value));
    }

    public void load (Reader stream) throws EvaluationException
    {
        try
        {
            ArrayList<ArrayList<Double>> temp = new ArrayList<ArrayList<Double>> ();
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
            String line = "";
            boolean comment = false;
            boolean done = false;
            while (stream.ready ()  &&  ! done)
            {
                token = (char) stream.read ();

                boolean processLine = false;
                switch (token)
                {
                    case '\r':
                        break;  // ignore CR characters
                    case '#':
                        comment = true;
                        break;
                    case '\n':
                        comment = false;
                    case ';':
                        if (! comment) processLine = true;
                        break;
                    case ']':
                        if (! comment)
                        {
                            done = true;
                            processLine = true;
                        }
                        break;
                    default:
                        if (! comment) line += token;
                }

                if (processLine)
                {
                    ArrayList<Double> row = new ArrayList<Double> ();
                    line = line.trim ();
                    while (line.length () > 0)
                    {
                        int                 position = line.indexOf (',' );  // The valid element delimiters are comma, space and tab
                        if (position == -1) position = line.indexOf (" " );
                        if (position == -1) position = line.indexOf ("\t");
                        if (position == -1)
                        {
                            row.add (Double.valueOf (line));
                            break;
                        }
                        else
                        {
                            row.add (Double.valueOf (line.substring (0, position)));
                        }
                        line = line.substring (position + 1);
                        line = line.trim ();
                    }
                    int c = row.size ();
                    if (c > 0)
                    {
                        temp.add (row);
                        columns = Math.max (columns, c);
                    }
                    line = "";
                }
            }

            // Assign elements to "value"
            int rows = temp.size ();
            if (transpose)
            {
                value = new double[rows][columns];
                for (int r = 0; r < rows; r++)
                {
                    ArrayList<Double> row = temp.get (r);
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
                    ArrayList<Double> row = temp.get (r);
                    for (int c = 0; c < row.size (); c++)
                    {
                        value[c][r] = row.get (c);
                    }
                }
            }

            if (value.length == 0  ||  value[1].length == 0) throw new EvaluationException ("Empty matrix");
        }
        catch (IOException error)
        {
            throw new EvaluationException ("Failed to convert string to matrix");
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

    public double getDouble (int row, int column)
    {
        return value[column][row];
    }

    public Scalar getScalar (int row, int column)
    {
        return new Scalar (value[column][row]);
    }

    public Type clear ()
    {
        int w = value.length;
        if (w < 1) return new Matrix ();
        int h = value[0].length;
        if (h < 1) return new Matrix ();
        return new Matrix (h, w);
    }

    public Type add (Type that) throws EvaluationException
    {
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            Matrix result = new Matrix (h, w);
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
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] + scalar;
                }
            }
            return result;
        }
        if (that instanceof Text) return add (new Matrix ((Text) that));
        throw new EvaluationException ("type mismatch");
    }

    public Type subtract (Type that) throws EvaluationException
    {
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            Matrix result = new Matrix (h, w);
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
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] - scalar;
                }
            }
            return result;
        }
        if (that instanceof Text) return subtract (new Matrix ((Text) that));
        throw new EvaluationException ("type mismatch");
    }

    public Type multiply (Type that) throws EvaluationException
    {
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int h = value[0].length;
            int w = B.length;
            int m = Math.min (value.length, B[0].length);
            Matrix result = new Matrix (h, w);
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
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] * scalar;
                }
            }
            return result;
        }
        if (that instanceof Text) return multiply (new Matrix ((Text) that));
        throw new EvaluationException ("type mismatch");
    }

    public Type multiplyElementwise (Type that) throws EvaluationException
    {
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            Matrix result = new Matrix (h, w);
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
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] * scalar;
                }
            }
            return result;
        }
        if (that instanceof Text) return multiply (new Matrix ((Text) that));
        throw new EvaluationException ("type mismatch");
    }

    public Type divide (Type that) throws EvaluationException
    {
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = value.length;
            int h = value[0].length;
            int ow = Math.min (w, B.length);
            int oh = Math.min (h, B[0].length);
            Matrix result = new Matrix (h, w);
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
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            int w = value.length;
            int h = value[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value[c][r] / scalar;
                }
            }
            return result;
        }
        if (that instanceof Text) return divide (new Matrix ((Text) that));
        throw new EvaluationException ("type mismatch");
    }

    public double det22 (int r0, int r1, int c0, int c1)
    {
        return (value[c0][r0] * value[c1][r1] - value[c1][r0] * value[c0][r1]);
    }

    public Type NOT () throws EvaluationException
    {
        int w = value.length;
        int h = value[0].length;
        if (h != w) throw new EvaluationException ("Can't invert non-square matrix (because we don't know about pseudo-inverse).");  // should create pseudo-inverse
        if (h == 1) return new Scalar (1 / value[0][0]);
        if (h == 2)
        {
            Matrix result = new Matrix (2, 2);

            double q = determinant ();
            if (q == 0) throw new EvaluationException ("invert: Matrix is singular!");

            result.value[0][0] = value[1][1] /  q;
            result.value[0][1] = value[0][1] / -q;
            result.value[1][0] = value[1][0] / -q;
            result.value[1][1] = value[0][0] /  q;

            return result;
        }
        if (h == 3)
        {
            Matrix result = new Matrix (3, 3);

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
        int w = value.length;
        int h = value[0].length;
        Matrix result = new Matrix (h, w);
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
        Matrix result = new Matrix (w, h);
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
        Matrix result = new Matrix (h, w);
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

    public boolean betterThan (Type that)
    {
        if (that instanceof Matrix) return false;
        if (that instanceof Text  ) return false;
        return true;
    }

    public String toString ()
    {
        StringWriter stream = new StringWriter ();

        int columns = value.length;
        if (columns == 0) return "[]";
        int rows    = value[0].length;
        if (rows    == 0) return "[]";

        stream.append ("[");
        int r = 0;
        while (true)
        {
            int c = 0;
            while (true)
            {
                stream.append (String.valueOf (value[c][r]));
                if (++c >= columns) break;
                stream.append (',');
            }

            if (++r >= rows) break;
            stream.append (";");
        }
        stream.append ("]");

        return stream.toString ();
    }
}
