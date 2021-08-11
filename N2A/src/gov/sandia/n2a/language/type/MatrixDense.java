/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    Matrix with a single block of storage and strided access pattern.
    Strided access allows us to wrap subregions (such as single columns or rows) and transposes
    around the same block of memory. This is arguably the most efficient representation of a dense matrix.
    Many of the functions here are adapted from FL MatrixStrided.
**/
public class MatrixDense extends Matrix
{
    protected double[] data;  // stored in column-major order
    protected int      offset;
    protected int      rows;
    protected int      columns;
    protected int      strideR;
    protected int      strideC;

    public MatrixDense ()
    {
    }

    public MatrixDense (int rows, int columns)
    {
        this (rows, columns, 0);
    }

    public MatrixDense (int rows, int columns, double initialValue)
    {
        this.rows    = rows;
        this.columns = columns;
        data         = new double[rows * columns];
        strideR      = 1;
        strideC      = rows;

        if (initialValue == 0) return;
        for (int i = 0; i < data.length; i++) data[i] = initialValue;
    }

    public MatrixDense (Matrix A)
    {
        columns = A.columns ();
        rows    = A.rows ();
        data    = new double[rows * columns];
        strideR = 1;
        strideC = rows;

        int i = 0;
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                data[i++] = A.get (r, c);
            }
        }
    }

    public MatrixDense (double[] data, int offset, int rows, int columns, int strideR, int strideC)
    {
        this.data    = data;
        this.offset  = offset;
        this.rows    = rows;
        this.columns = columns;
        this.strideR = strideR;
        this.strideC = strideC;
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
            int tempC = 0;
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
                            tempC = Math.max (tempC, c);
                            row = new ArrayList<Double> (tempC);
                        }
                        break;
                    default:
                        buffer[index++] = token;  // If we overrun the buffer, we should automatically get an index out of range error.
                }
            }

            // Assign elements to "data"
            int tempR = temp.size ();
            data      = new double[tempR * tempC];
            offset    = 0;
            for (int r = 0; r < tempR; r++)
            {
                row = temp.get (r);
                int i = r;
                for (int c = 0; c < row.size (); c++)
                {
                    data[i] = row.get (c);
                    i += tempR;
                }
            }
            if (transpose)
            {
                rows    = tempC;
                columns = tempR;
                strideR = tempC;
                strideC = 1;
            }
            else
            {
                rows    = tempR;
                columns = tempC;
                strideR = 1;
                strideC = tempR;
            }

            if (data.length == 0) throw new EvaluationException ("Empty matrix");
        }
        catch (IOException error)
        {
            throw new EvaluationException ("Failed to convert input to matrix");
        }
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
        return data[offset + row * strideR + column * strideC];
    }

    public double get (int row)
    {
        return data[offset + row * strideR];
    }

    public MatrixDense getColumn (int column)
    {
        return new MatrixDense (data, offset + column * strideC, rows, 1, strideR, strideC);
    }

    public MatrixDense getRow (int row)
    {
        return new MatrixDense (data, offset + row * strideR, 1, columns, strideR, strideC);
    }

    public MatrixDense getRegion (int firstRow, int firstColumn)
    {
        return getRegion (firstRow, firstColumn, rows - 1, columns - 1);
    }

    public MatrixDense getRegion (int firstRow, int firstColumn, int lastRow, int lastColumn)
    {
        int offset  = this.offset + firstColumn * strideC + firstRow * strideR;
        int rows    = lastRow    - firstRow    + 1;
        int columns = lastColumn - firstColumn + 1;
        return new MatrixDense (data, offset, rows, columns, strideR, strideC);
    }

    public MatrixDense transpose ()
    {
        return new MatrixDense (data, offset, columns, rows, strideC, strideR);
    }

    /**
        If this matrix is single-column (a vector), then the raw data array is a useful
        alternate way to access the elements.
    **/
    public double[] getData ()
    {
        return data;
    }

    public void set (int row, int column, double a)
    {
        data[offset + row * strideR + column * strideC] = a;
    }

    public void set (int row, double a)
    {
        data[offset + row * strideR] = a;
    }

    public MatrixDense clear ()
    {
        return new MatrixDense (rows, columns);
    }

    public MatrixDense clear (double initialValue)
    {
        return new MatrixDense (rows, columns, initialValue);
    }

    /**
        @return A copy of this object, with diagonal elements set to 1 and off-diagonals set to zero.
     */
    public MatrixDense identity ()
    {
        MatrixDense result = new MatrixDense (rows, columns);
        int h = Math.min (rows, columns);
        for (int r = 0; r < h; r++) result.data[r * (columns + 1)] = 1;
        return result;
    }

    /**
        Only examines elements within the defined region, rather than entire data block.
    **/
    public boolean isZero ()
    {
        int step = strideC - rows * strideR;
        int i = offset;
        int r = 0;
        int end = rows * columns;
        while (r < end)
        {
            int columnEnd = r + rows;
            while (r++ < columnEnd)
            {
                if (data[i] != 0) return false;
                i += strideR;
            }
            i += step;
        }
        return true;
    }

    public Type add (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int oh = Math.min (rows,    B.rows);
            int ow = Math.min (columns, B.columns);
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - oh   * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * ow;
            while (r < end)
            {
                int overlapEnd = r + oh;
                int columnEnd  = r + rows;
                while (r < overlapEnd)
                {
                    result.data[r++] = data[a] + B.data[b];
                    a +=   strideR;
                    b += B.strideR;
                }
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
                b += stepB;
            }
            end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }

            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int oh = Math.min (rows,    B.rows ());
            int ow = Math.min (columns, B.columns ());
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA = strideC - rows * strideR;
            int a = offset;
            int r = 0;
            for (int col = 0; col < ow; col++)
            {
                int row = 0;
                for (; row < oh; row++)
                {
                    result.data[r++] = data[a] + B.get (row, col);
                    a += strideR;
                }
                for (; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            for (int col = ow; col < columns; col++)
            {
                for (int row = 0; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i = offset;
            int r = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[i] + scalar;
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        if (that instanceof Text) return new Text (toString ()).add (that);
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense subtract (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int oh = Math.min (rows,    B.rows);
            int ow = Math.min (columns, B.columns);
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - oh   * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * ow;
            while (r < end)
            {
                int overlapEnd = r + oh;
                int columnEnd  = r + rows;
                while (r < overlapEnd)
                {
                    result.data[r++] = data[a] - B.data[b];
                    a +=   strideR;
                    b += B.strideR;
                }
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
                b += stepB;
            }
            end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }

            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int oh = Math.min (rows,    B.rows ());
            int ow = Math.min (columns, B.columns ());
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA = strideC - rows * strideR;
            int a = offset;
            int r = 0;
            for (int col = 0; col < ow; col++)
            {
                int row = 0;
                for (; row < oh; row++)
                {
                    result.data[r++] = data[a] - B.get (row, col);
                    a += strideR;
                }
                for (; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            for (int col = ow; col < columns; col++)
            {
                for (int row = 0; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i   = offset;
            int r   = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[i] - scalar;
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense multiply (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int h = rows;
            int w = B.columns;
            int m = Math.min (columns, B.rows);
            MatrixDense result = new MatrixDense (h, w);
            int b = B.offset;
            int r = 0;
            int end = rows * B.columns;
            while (r < end)
            {
                int a = offset;
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    double sum = 0;
                    int i = a;
                    int j = b;
                    int rowEnd = j + m * B.strideR;
                    while (j != rowEnd)
                    {
                        sum += data[i] * B.data[j];
                        i +=   strideC;
                        j += B.strideR;
                    }
                    result.data[r++] = sum;
                    a += strideR;
                }
                b += B.strideC;
            }
            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int h = rows;
            int w = B.columns ();
            int m = Math.min (columns, B.rows ());
            MatrixDense result = new MatrixDense (h, w);
            int r = 0;
            for (int col = 0; col < w; col++)
            {
                int a = offset;
                for (int row = 0; row < h; row++)
                {
                    double sum = 0;
                    int i = a;
                    for (int j = 0; j < m; j++)
                    {
                        sum += data[i] * B.get (j, col);
                        i += strideC;
                    }
                    result.data[r++] = sum;
                    a += strideR;
                }
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i   = offset;
            int r   = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[i] * scalar;
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense multiplyElementwise (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int oh = Math.min (rows,    B.rows);
            int ow = Math.min (columns, B.columns);
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - oh   * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * ow;
            while (r < end)
            {
                int overlapEnd = r + oh;
                int columnEnd  = r + rows;
                while (r < overlapEnd)
                {
                    result.data[r++] = data[a] * B.data[b];
                    a +=   strideR;
                    b += B.strideR;
                }
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
                b += stepB;
            }
            end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }

            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int oh = Math.min (rows,    B.rows ());
            int ow = Math.min (columns, B.columns ());
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA = strideC - rows * strideR;
            int a = offset;
            int r = 0;
            for (int col = 0; col < ow; col++)
            {
                int row = 0;
                for (; row < oh; row++)
                {
                    result.data[r++] = data[a] * B.get (row, col);
                    a += strideR;
                }
                for (; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            for (int col = ow; col < columns; col++)
            {
                for (int row = 0; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i = offset;
            int r = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[i] * scalar;
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense divide (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int oh = Math.min (rows,    B.rows);
            int ow = Math.min (columns, B.columns);
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - oh   * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * ow;
            while (r < end)
            {
                int overlapEnd = r + oh;
                int columnEnd  = r + rows;
                while (r < overlapEnd)
                {
                    result.data[r++] = data[a] / B.data[b];
                    a +=   strideR;
                    b += B.strideR;
                }
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
                b += stepB;
            }
            end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }

            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int oh = Math.min (rows,    B.rows ());
            int ow = Math.min (columns, B.columns ());
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA = strideC - rows * strideR;
            int a = offset;
            int r = 0;
            for (int col = 0; col < ow; col++)
            {
                int row = 0;
                for (; row < oh; row++)
                {
                    result.data[r++] = data[a] / B.get (row, col);
                    a += strideR;
                }
                for (; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            for (int col = ow; col < columns; col++)
            {
                for (int row = 0; row < rows; row++)
                {
                    result.data[r++] = data[a];
                    a += strideR;
                }
                a += stepA;
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i = offset;
            int r = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = data[i] / scalar;
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense min (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int oh = Math.min (rows,    B.rows);
            int ow = Math.min (columns, B.columns);
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - oh   * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * ow;
            while (r < end)
            {
                int overlapEnd = r + oh;
                int columnEnd  = r + rows;
                while (r < overlapEnd)
                {
                    result.data[r++] = Math.min (data[a], B.data[b]);
                    a +=   strideR;
                    b += B.strideR;
                }
                while (r < columnEnd)
                {
                    result.data[r++] = Math.min (data[a], 0);
                    a += strideR;
                }
                a += stepA;
                b += stepB;
            }
            end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = Math.min (data[a], 0);
                    a += strideR;
                }
                a += stepA;
            }

            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int oh = Math.min (rows,    B.rows ());
            int ow = Math.min (columns, B.columns ());
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA = strideC - rows * strideR;
            int a = offset;
            int r = 0;
            for (int col = 0; col < ow; col++)
            {
                int row = 0;
                for (; row < oh; row++)
                {
                    result.data[r++] = Math.min (data[a],  B.get (row, col));
                    a += strideR;
                }
                for (; row < rows; row++)
                {
                    result.data[r++] = Math.min (data[a], 0);
                    a += strideR;
                }
                a += stepA;
            }
            for (int col = ow; col < columns; col++)
            {
                for (int row = 0; row < rows; row++)
                {
                    result.data[r++] = Math.min (data[a], 0);
                    a += strideR;
                }
                a += stepA;
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i = offset;
            int r = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = Math.min (data[i], scalar);
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense max (Type that) throws EvaluationException
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int oh = Math.min (rows,    B.rows);
            int ow = Math.min (columns, B.columns);
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - oh   * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * ow;
            while (r < end)
            {
                int overlapEnd = r + oh;
                int columnEnd  = r + rows;
                while (r < overlapEnd)
                {
                    result.data[r++] = Math.max (data[a], B.data[b]);
                    a +=   strideR;
                    b += B.strideR;
                }
                while (r < columnEnd)
                {
                    result.data[r++] = Math.max (data[a], 0);
                    a += strideR;
                }
                a += stepA;
                b += stepB;
            }
            end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = Math.max (data[a], 0);
                    a += strideR;
                }
                a += stepA;
            }

            return result;
        }
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int oh = Math.min (rows,    B.rows ());
            int ow = Math.min (columns, B.columns ());
            MatrixDense result = new MatrixDense (rows, columns);
            int stepA = strideC - rows * strideR;
            int a = offset;
            int r = 0;
            for (int col = 0; col < ow; col++)
            {
                int row = 0;
                for (; row < oh; row++)
                {
                    result.data[r++] = Math.max (data[a],  B.get (row, col));
                    a += strideR;
                }
                for (; row < rows; row++)
                {
                    result.data[r++] = Math.max (data[a], 0);
                    a += strideR;
                }
                a += stepA;
            }
            for (int col = ow; col < columns; col++)
            {
                for (int row = 0; row < rows; row++)
                {
                    result.data[r++] = Math.max (data[a], 0);
                    a += strideR;
                }
                a += stepA;
            }
            return result;
        }
        if (that instanceof Scalar)
        {
            double scalar = ((Scalar) that).value;
            MatrixDense result = new MatrixDense (rows, columns);
            int step = strideC - rows * strideR;
            int i = offset;
            int r = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r < columnEnd)
                {
                    result.data[r++] = Math.max (data[i], scalar);
                    i += strideR;
                }
                i += step;
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public MatrixDense negate () throws EvaluationException
    {
        MatrixDense result = new MatrixDense (rows, columns);
        int step = strideC - rows * strideR;
        int i = offset;
        int r = 0;
        int end = rows * columns;
        while (r < end)
        {
            int columnEnd = r + rows;
            while (r < columnEnd)
            {
                result.data[r++] = -data[i];
                i += strideR;
            }
            i += step;
        }
        return result;
    }

    public MatrixDense visit (Visitor visitor)
    {
        MatrixDense result = new MatrixDense (rows, columns);
        int step = strideC - rows * strideR;
        int i = offset;
        int r = 0;
        int end = rows * columns;
        while (r < end)
        {
            int columnEnd = r + rows;
            while (r < columnEnd)
            {
                result.data[r++] = visitor.apply (data[i]);
                i += strideR;
            }
            i += step;
        }
        return result;
    }

    public double determinant () throws EvaluationException
    {
        if (rows != columns) throw new EvaluationException ("Can't compute determinant of non-square matrix.");
        if (rows == 1) return data[offset];
        if (rows == 2) return get (0, 0) * get (1, 1) - get (0, 1) * get (1, 0);
        if (rows == 3)
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
        double result = 0;

        int step = strideC - rows * strideR;
        int i = offset;
        int r = 0;
        int end = rows * columns;

        if (n == 0)
        {
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r++ < columnEnd)
                {
                    if (data[i] != 0) result++;
                    i += strideR;
                }
                i += step;
            }
        }
        else if (n == 1)
        {
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r++ < columnEnd)
                {
                    result += Math.abs (data[i]);
                    i += strideR;
                }
                i += step;
            }
        }
        else if (n == 2)
        {
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r++ < columnEnd)
                {
                    double d = data[i];
                    result += d * d;
                    i += strideR;
                }
                i += step;
            }
            result = Math.sqrt (result);
        }
        else if (n == Double.POSITIVE_INFINITY)
        {
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r++ < columnEnd)
                {
                    result = Math.max (result, Math.abs (data[i]));
                    i += strideR;
                }
                i += step;
            }
        }
        else
        {
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r++ < columnEnd)
                {
                    result += Math.pow (data[i], n);
                    i += strideR;
                }
                i += step;
            }
            result = Math.pow (result, 1 / n);
        }
        return result;
    }

    public int compareTo (Type that)
    {
        if (that instanceof MatrixDense)
        {
            MatrixDense B = (MatrixDense) that;
            int difference = columns - B.columns;
            if (difference != 0) return difference;
            difference = rows - B.rows;
            if (difference != 0) return difference;

            int stepA =   strideC - rows *   strideR;
            int stepB = B.strideC - rows * B.strideR;
            int a =   offset;
            int b = B.offset;
            int r = 0;
            int end = rows * columns;
            while (r < end)
            {
                int columnEnd = r + rows;
                while (r++ < columnEnd)
                {
                    double d = data[a] - B.data[b];
                    if (d > 0) return  1;
                    if (d < 0) return -1;
                    a +=   strideR;
                    b += B.strideR;
                }
                a += stepA;
                b += stepB;
            }
            return 0;
        }
        if (that instanceof Scalar)
        {
            if (columns != 1) return 1;
            if (rows    != 1) return 1;
            double d = data[offset] - ((Scalar) that).value;
            if (d > 0) return 1;
            if (d < 0) return -1;
            return 0;
        }
        throw new EvaluationException ("type mismatch");
    }
}
