/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.type;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

/**
    String type.
**/
public class Text extends Type
{
    public String value;

    public Text ()
    {
        value = "";
    }

    public Text (String value)
    {
        this.value = value;
    }

    public Text (Type that)
    {
        value = that.toString ();
    }

    public Type clear ()
    {
        return new Text ();
    }

    public void addEscapes ()
    {
        StringBuffer result = new StringBuffer ();
        for (int i = 0; i < value.length (); i++)
        {
            char ch = value.charAt (i);
            switch (ch)
            {
                case '\b': result.append ("\\b" ); break;
                case '\t': result.append ("\\t" ); break;
                case '\n': result.append ("\\n" ); break;
                case '\f': result.append ("\\f" ); break;
                case '\r': result.append ("\\r" ); break;
                case '\"': result.append ("\\\""); break;
                case '\'': result.append ("\\\'"); break;
                case '\\': result.append ("\\\\"); break;
                default:
                    if (ch < 0x20 || ch > 0x7e)
                    {
                        String s = "0000" + Integer.toString (ch, 16);
                        result.append ("\\u" + s.substring (s.length () - 4, s.length ()));
                    }
                    else
                    {
                        result.append (ch);
                    }
            }
        }
        value = result.toString ();
    }

    public void removeEscapes ()
    {
        StringBuffer result = new StringBuffer ();
        for (int i = 0; i < value.length (); i++)
        {
            char ch = value.charAt (i);
            if (ch != '\\')
            {
                result.append (ch);
            }
            else if (++i < value.length ())  // Note: a bare backslash at the end of the line simply disappears.
            {
                ch = value.charAt (i);
                switch (ch)
                {
                    case 'b' : result.append ("\b"); break;
                    case 't' : result.append ("\t"); break;
                    case 'n' : result.append ("\n"); break;
                    case 'f' : result.append ("\f"); break;
                    case 'r' : result.append ("\r"); break;
                    case '"' : result.append ("\""); break;
                    case '\'': result.append ("'" ); break;
                    case '\\': result.append ("\\"); break;
                    case 'u' :
                        int i4 = i + 4;
                        if (i4 < value.length ())
                        {
                            result.append (Character.toChars (Integer.parseInt (value.substring (i, i4), 16)));
                            i = i4;
                        }
                    // no default; invalid escapes simply disappear
                }
            }
        }
        value = result.toString ();
    }

    public Type add (Type that)
    {
        return new Text (value + that.toString ());
    }

    public Type min (Type that)
    {
        if (compareTo (that) > 0) return new Text (that);
        return this;
    }

    public Type max (Type that)
    {
        if (compareTo (that) < 0) return new Text (that);
        return this;
    }

    public Type EQ (Type that) throws EvaluationException
    {
        return new Scalar (value.equals (that.toString ()) ? 1 : 0);
    }

    public Type NE (Type that) throws EvaluationException
    {
        return new Scalar (value.equals (that.toString ()) ? 0 : 1);
    }

    public Type GT (Type that) throws EvaluationException
    {
        int result = value.compareTo (that.toString ());
        return new Scalar ((result == 1) ? 1 : 0);
    }

    public Type GE (Type that) throws EvaluationException
    {
        int result = value.compareTo (that.toString ());
        return new Scalar ((result >= 0) ? 1 : 0);
    }

    public Type LT (Type that) throws EvaluationException
    {
        int result = value.compareTo (that.toString ());
        return new Scalar ((result == -1) ? 1 : 0);
    }

    public Type LE (Type that) throws EvaluationException
    {
        int result = value.compareTo (that.toString ());
        return new Scalar ((result <= 0) ? 1 : 0);
    }

    public boolean betterThan (Type that)
    {
        if (that instanceof Text    ) return false;
        if (that instanceof Instance) return false;
        return true;
    }

    public String toString ()
    {
        return value;
    }

    public int compareTo (Type that)
    {
        if (that instanceof Text    ) return value.compareTo (((Text) that).value);
        if (that instanceof Scalar  ) return new Double (value).compareTo (new Double (((Scalar) that).value));
        if (that instanceof Matrix  ) return new Matrix (this).compareTo (that);
        if (that instanceof Instance) return -1;
        throw new EvaluationException ("type mismatch");
    }
}
