package gov.sandia.n2a.language;

/**
    Holds data from one of N2A's basic types, and knows how to perform operations with all other types.
    Encodes rules for automatic type promotion in the context of an operation.
**/
public abstract class Type
{
    public Type add (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type subtract (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type multiply (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type divide (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type modulo (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type power (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type EQ (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type NE (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type GT (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type GE (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type LT (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type LE (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type AND (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type OR (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type NOT () throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type negate () throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public Type transpose () throws EvaluationException
    {
        throw new EvaluationException ("Operation not supported on this type.");
    }

    public boolean betterThan (Type that)
    {
        return false;
    }
}
