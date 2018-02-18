/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.type;

import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.operator.Negate;

/**
    Floating-point type.
**/
public class Scalar extends Type
{
    public double value;

    public Scalar ()
    {
    }

    public Scalar (double value)
    {
        this.value = value;
    }

    public Scalar (Text that)
    {
        this.value = Double.valueOf (that.value);
    }

    public Scalar (Matrix that)
    {
        value = that.get (0);
    }

    /**
        General utility: given a string containing a number with units, convert to the scaled SI value.
    **/
    public static double convert (String expression)
    {
        try
        {
            Operator op = Operator.parse (expression);
            double sign = 1;
            if (op instanceof Negate)
            {
                op = ((Negate) op).operand;
                sign = -1;
            }
            if (! (op instanceof Constant)) return 0;
            Type result = ((Constant) op).value;
            if (result instanceof Scalar) return ((Scalar) result).value * sign;
        }
        catch (ParseException e)
        {
        }
        return 0;
    }

    public Type clear ()
    {
        return new Scalar (0);
    }

    public Type add (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value + ((Scalar) that).value);
        if (that instanceof Text  ) return new Text   (value + ((Text)   that).value);
        if (that instanceof Matrix) return that.add (this);
        throw new EvaluationException ("type mismatch");
    }

    public Type subtract (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value - ((Scalar) that).value);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value - B.get (r, c);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type multiply (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value * ((Scalar) that).value);
        if (that instanceof Matrix) return that.multiply (this);
        throw new EvaluationException ("type mismatch");
    }

    public Type multiplyElementwise (Type that) throws EvaluationException
    {
        if (that instanceof Matrix) return that.multiplyElementwise (this);
        if (that instanceof Scalar) return new Scalar (value * ((Scalar) that).value);
        throw new EvaluationException ("type mismatch");
    }

    public Type divide (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value / ((Scalar) that).value);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value / B.get (r, c);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type modulo (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value % ((Scalar) that).value);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value % B.get (r, c);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type power (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (Math.pow (value, ((Scalar) that).value));
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.pow (value, B.get (r, c));
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type min (Type that)
    {
        if (that instanceof Scalar) return new Scalar (Math.min (value, ((Scalar) that).value));
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.min (value, B.get (r, c));
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type max (Type that)
    {
        if (that instanceof Scalar) return new Scalar (Math.max (value, ((Scalar) that).value));
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.max (value, B.get (r, c));
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type EQ (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value == ((Scalar) that).value) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value == B.get (r, c)) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type NE (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value != ((Scalar) that).value) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value != B.get (r, c)) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type GT (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value > ((Scalar) that).value) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value > B.get (r, c)) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type GE (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value >= ((Scalar) that).value) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value >= B.get (r, c)) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type LT (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value < ((Scalar) that).value) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value < B.get (r, c)) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type LE (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value <= ((Scalar) that).value) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value <= B.get (r, c)) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type AND (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value * ((Scalar) that).value != 0) ? 1 : 0);
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value * B.get (r, c) != 0) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type OR (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((Math.abs (value) + Math.abs (((Scalar) that).value) != 0) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double scalar = Math.abs (value);
            Matrix B = (Matrix) that;
            int w = B.columns ();
            int h = B.rows ();
            MatrixDense result = new MatrixDense (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (scalar + Math.abs (B.get (r, c)) != 0) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type NOT () throws EvaluationException
    {
        return new Scalar ((value == 0) ? 1 : 0);
    }

    public Type negate () throws EvaluationException
    {
        return new Scalar (-value);
    }

    public Type transpose () throws EvaluationException
    {
        return this;
    }

    public String toString ()
    {
        if ((int) value == value) return String.valueOf ((int) value);  // simplify display a little, since we don't distinguish float from int
        return String.valueOf (value);
    }

    public int compareTo (Type that)
    {
        if (that instanceof Scalar) return new Double (value).compareTo (new Double (((Scalar) that).value));
        if (that instanceof Matrix)
        {
            Matrix B = (Matrix) that;
            int w = B.columns ();
            if (w != 1) return -1;
            int h = B.rows ();
            if (h != 1) return -1;
            return new Double (value).compareTo (new Double (B.get (0)));
        }
        if (that instanceof Instance) return -1;
        throw new EvaluationException ("type mismatch");
    }
}
