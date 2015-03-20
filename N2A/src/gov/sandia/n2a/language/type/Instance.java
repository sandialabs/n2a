package gov.sandia.n2a.language.type;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

/**
    Instance type. Represents a entire concrete object at simulation time.
**/
public class Instance extends Type
{
    EquationSet value;

    public Instance ()
    {
        // value is null
    }

    public Instance (EquationSet value)
    {
        this.value = value;
    }

    // TODO: these comparisons are currently meaningless, because they are based on EquationSet (the class) rather than distinct runtime instances.
    // For example, if two endpoints of a connection go to parts of the same type, then EQ will evaluate to true, regardless of whether there were a self-connection at runtime or not.
    // Part of the reason for this is that we don't actually have a proper interpreter built yet, so there aren't any true instances!

    public Type EQ (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Instance shouldn't be evaluated staticly");
        //if (that instanceof Instance) return new Scalar (value.equals (((Instance) that).value) ? 1 : 0);
        //throw new EvaluationException ("Instances can only be compared to Instances");
    }

    public Type NE (Type that) throws EvaluationException
    {
        throw new EvaluationException ("Instance shouldn't be evaluated staticly");
        //if (that instanceof Instance) return new Scalar (value.equals (((Instance) that).value) ? 0 : 1);
        //throw new EvaluationException ("Instances can only be compared to Instances");
    }

    public String toString ()
    {
        return value.name;
    }
}
