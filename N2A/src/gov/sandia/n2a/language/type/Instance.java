/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.type;

import java.util.Iterator;

import gov.sandia.n2a.backend.internal.Connection;
import gov.sandia.n2a.backend.internal.Part;
import gov.sandia.n2a.backend.internal.Euler;
import gov.sandia.n2a.backend.internal.Population;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

/**
    Instance type. Represents a entire concrete object at simulation time.
**/
public class Instance extends Type
{
    public EquationSet equations;
    public Instance    container;
    public float[]     valuesFloat;  // memory is the premium resource, not accuracy
    public Type[]      valuesType;

    public void allocate (int countFloat, int countType)
    {
        if (countFloat > 0) valuesFloat = new float[countFloat];
        if (countType  > 0) valuesType  = new Type [countType ];
    }

    public void resolve (Variable v)
    {
        if (v != v.reference.variable)
        {
            Instance result = this;
            Iterator<Object> it = v.reference.resolution.iterator ();
            while (it.hasNext ())
            {
                int i = ((Integer) it.next ()).intValue ();
                if      (i > 0) result = ((Part)       result).populations[i-1];
                else if (i < 0) result = ((Connection) result).endpoints [-i-1];
                else  // i == 0
                {
                    if (result instanceof Population) result = result.container;
                    else                              result = result.container.container;  // Parts must dereference their Population to get to their true container.
                }
            }
            valuesType[v.readIndex] = result;
        }
    }

    public Type get (Variable v)
    {
        if (v.reference.variable != v) return ((Instance) valuesType[v.readIndex]).get (v.reference.variable);
        if (v.type instanceof Scalar) return new Scalar (valuesFloat[v.readIndex]);
        Type result = valuesType[v.readIndex];
        if (result == null) return v.type;  // assumes that we never modify the returned object, and that previously it was set to the equivalent of 0
        return result;
    }

    public void set (Variable v, Type value)
    {
        if (v.reference.variable != v)
        {
            ((Instance) valuesType[v.readIndex]).set (v.reference.variable, value);
        }
        else
        {
            if (v.type instanceof Scalar) valuesFloat[v.writeIndex] = (float) ((Scalar) value).value;
            else                          valuesType [v.writeIndex] = value;
        }
    }

    public Type getFinal (Variable v)
    {
        // Don't check for reference, because getFinal() should never be called in that case.
        if (v.type instanceof Scalar) return new Scalar (valuesFloat[v.writeIndex]);
        Type result = valuesType[v.writeIndex];
        if (result == null) return v.type;
        return result;
    }

    public void setFinal (Variable v, Type value)
    {
        // Note the change from writeIndex to readIndex. That's key purpose of this method.
        if (v.type instanceof Scalar) valuesFloat[v.readIndex] = (float) ((Scalar) value).value;
        else                          valuesType [v.readIndex] = value;
    }

    public void init (Euler simulator)
    {
    }

    public void integrate (Euler simulator)
    {
    }

    public void prepare ()
    {
    }

    public void update (Euler simulator)
    {
    }

    /**
        Finalize the values of buffered variables, and complete any other housekeeping for current simulation cycle.
        @return true to remain in simulation queue. false to be removed from simulation.
    **/
    public boolean finish (Euler simulator)
    {
        return true;
    }

    public Type EQ (Type that) throws EvaluationException
    {
        if (this == that) return new Scalar (1);
        return new Scalar (0);
    }

    public Type NE (Type that) throws EvaluationException
    {
        if (this == that) return new Scalar (0);
        return new Scalar (1);
    }

    public Type GT (Type that) throws EvaluationException
    {
        if (! (that instanceof Instance)) throw new EvaluationException ("Type mismatch");
        if (this.hashCode () > that.hashCode ()) return new Scalar (1);
        return new Scalar (0);
    }

    public Type GE (Type that) throws EvaluationException
    {
        if (this == that) return new Scalar (1);
        if (! (that instanceof Instance)) throw new EvaluationException ("Type mismatch");
        if (this.hashCode () > that.hashCode ()) return new Scalar (1);
        return new Scalar (0);
    }

    public Type LT (Type that) throws EvaluationException
    {
        if (! (that instanceof Instance)) throw new EvaluationException ("Type mismatch");
        if (this.hashCode () < that.hashCode ()) return new Scalar (1);
        return new Scalar (0);
    }

    public Type LE (Type that) throws EvaluationException
    {
        if (this == that) return new Scalar (1);
        if (! (that instanceof Instance)) throw new EvaluationException ("Type mismatch");
        if (this.hashCode () < that.hashCode ()) return new Scalar (1);
        return new Scalar (0);
    }

    public String toString ()
    {
        return equations.name;
    }
}
