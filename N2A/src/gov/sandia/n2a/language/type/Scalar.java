/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.type;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

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
        if (that.value.length > 0) value = that.value[0][0];
    }

    public Type clear ()
    {
        return new Scalar (0);
    }

    public Type add (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value + ((Scalar) that).value);
        if (that instanceof Text  ) return new Text   (value + ((Text)   that).value);  // Although inconsistent with all other operations where scalar is on left, this is more useful as it allows arbitrary string construction.
        if (that instanceof Matrix) return that.add (this);
        throw new EvaluationException ("type mismatch");
    }

    public Type subtract (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value - ((Scalar) that).value);
        if (that instanceof Text  ) return new Scalar (value - Double.valueOf (((Text) that).value));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value - B[c][r];
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type multiply (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value * ((Scalar) that).value);
        if (that instanceof Text  ) return new Scalar (value * Double.valueOf (((Text) that).value));
        if (that instanceof Matrix) return that.multiply (this);
        throw new EvaluationException ("type mismatch");
    }

    public Type multiplyElementwise (Type that) throws EvaluationException
    {
        if (that instanceof Matrix) return that.multiplyElementwise (this);
        if (that instanceof Scalar) return new Scalar (value * ((Scalar) that).value);
        if (that instanceof Text  ) return new Scalar (value * Double.valueOf (((Text) that).value));
        throw new EvaluationException ("type mismatch");
    }

    public Type divide (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value / ((Scalar) that).value);
        if (that instanceof Text  ) return new Scalar (value / Double.valueOf (((Text) that).value));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value / B[c][r];
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type modulo (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (value % ((Scalar) that).value);
        if (that instanceof Text  ) return new Scalar (value % Double.valueOf (((Text) that).value));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = value % B[c][r];
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type power (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar (Math.pow (value, ((Scalar) that).value));
        if (that instanceof Text  ) return new Scalar (Math.pow (value, Double.valueOf (((Text) that).value)));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.pow (value, B[c][r]);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type min (Type that)
    {
        if (that instanceof Scalar) return new Scalar (Math.min (value, ((Scalar) that).value));
        if (that instanceof Text  ) return new Scalar (Math.min (value, Double.valueOf (((Text) that).value)));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.min (value, B[c][r]);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type max (Type that)
    {
        if (that instanceof Scalar) return new Scalar (Math.max (value, ((Scalar) that).value));
        if (that instanceof Text  ) return new Scalar (Math.max (value, Double.valueOf (((Text) that).value)));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = Math.max (value, B[c][r]);
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type EQ (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value == ((Scalar) that).value               ) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value == Double.valueOf (((Text) that).value)) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value == B[c][r]) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type NE (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value != ((Scalar) that).value               ) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value != Double.valueOf (((Text) that).value)) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value != B[c][r]) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type GT (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value > ((Scalar) that).value               ) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value > Double.valueOf (((Text) that).value)) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value > B[c][r]) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type GE (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value >= ((Scalar) that).value               ) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value >= Double.valueOf (((Text) that).value)) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value >= B[c][r]) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type LT (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value < ((Scalar) that).value               ) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value < Double.valueOf (((Text) that).value)) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value < B[c][r]) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type LE (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value <= ((Scalar) that).value               ) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value <= Double.valueOf (((Text) that).value)) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value <= B[c][r]) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type AND (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((value * ((Scalar) that).value                != 0) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((value * Double.valueOf (((Text) that).value) != 0) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (value * B[c][r] != 0) ? 1 : 0;
                }
            }
            return result;
        }
        throw new EvaluationException ("type mismatch");
    }

    public Type OR (Type that) throws EvaluationException
    {
        if (that instanceof Scalar) return new Scalar ((Math.abs (value) + Math.abs (((Scalar) that).value)                != 0) ? 1 : 0);
        if (that instanceof Text  ) return new Scalar ((Math.abs (value) + Math.abs (Double.valueOf (((Text) that).value)) != 0) ? 1 : 0);
        if (that instanceof Matrix)
        {
            double scalar = Math.abs (value);
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            int h = B[0].length;
            Matrix result = new Matrix (h, w);
            for (int c = 0; c < w; c++)
            {
                for (int r = 0; r < h; r++)
                {
                    result.value[c][r] = (scalar + Math.abs (B[c][r]) != 0) ? 1 : 0;
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
        if (that instanceof Text  ) return new Double (value).compareTo (Double.valueOf (((Text) that).value));
        if (that instanceof Matrix)
        {
            double[][] B = ((Matrix) that).value;
            int w = B.length;
            if (w != 1) return -1;
            int h = B[0].length;
            if (h != 1) return -1;
            return new Double (value).compareTo (new Double (B[0][0]));
        }
        if (that instanceof Instance) return -1;
        throw new EvaluationException ("type mismatch");
    }
}
