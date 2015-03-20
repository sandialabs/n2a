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

    public Type add (Type that)
    {
        return new Text (value + that.toString ());
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
        if (that instanceof Text) return false;
        return true;
    }

    public String toString ()
    {
        return value;
    }
}
