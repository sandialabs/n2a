/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.type;

import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import gov.sandia.n2a.backend.internal.Connection;
import gov.sandia.n2a.backend.internal.InternalBackendData;
import gov.sandia.n2a.backend.internal.Part;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.backend.internal.Population;
import gov.sandia.n2a.backend.internal.Wrapper;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;

/**
    Instance type. Represents a entire concrete object at simulation time.
**/
public class Instance extends Type
{
    public EquationSet equations;
    public Instance    container;
    public float[]     valuesFloat;  // memory is the premium resource, not accuracy, so use float rather than double
    public Object[]    valuesObject;
    public Instance    next;      // for doubly-linked list
    public Instance    previous;  // for doubly-linked list

    public void allocate (int countFloat, int countObject)
    {
        if (countFloat  > 0) valuesFloat  = new float [countFloat];
        if (countObject > 0) valuesObject = new Object[countObject];
    }

    public void resolve (TreeSet<VariableReference> references)
    {
        for (VariableReference r : references)
        {
            Instance result = this;
            Iterator<Object> it = r.resolution.iterator ();
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
            valuesObject[r.index] = result;
        }
    }

    /**
        Fetches a value from a referenced instance.
    **/
    public Type get (VariableReference r)
    {
        if (r.index >= 0) return ((Instance) valuesObject[r.index]).get (r.variable);
        return get (r.variable);
    }

    /**
        Fetches a value local to this instance. Value may also be a constant (and thus not stored).
    **/
    public Type get (Variable v)
    {
        if (v.readIndex < 0) return v.type;
        if (v.type instanceof Scalar) return new Scalar (valuesFloat[v.readIndex]);
        Type result = (Type) valuesObject[v.readIndex];
        if (result == null) return v.type;  // assumes that we never modify the returned object, and that previously it was set to the equivalent of 0
        return result;
    }

    /**
        Stores a value, either local or referenced.
    **/
    public void set (Variable v, Type value)
    {
        if (v.reference.variable != v)
        {
            ((Instance) valuesObject[v.reference.index]).set (v.reference.variable, value);
        }
        else
        {
            if (v.type instanceof Scalar) valuesFloat [v.writeIndex] = (float) ((Scalar) value).value;
            else                          valuesObject[v.writeIndex] = value;
        }
    }

    /**
        Fetch a value from reference for the purpose of moving into storage to keep past the end of current cycle.
    **/
    public Type getFinal (VariableReference r)
    {
        if (r.index >= 0) return ((Instance) valuesObject[r.index]).getFinal (r.variable);
        return getFinal (r.variable);
    }

    /**
        Fetch a local temporary value for the purpose of moving into storage to keep past the end of current cycle.
    **/
    public Type getFinal (Variable v)
    {
        if (v.type instanceof Scalar) return new Scalar (valuesFloat[v.writeIndex]);
        Type result = (Type) valuesObject[v.writeIndex];
        if (result == null) return v.type;
        return result;
    }

    /**
        Stores a local value to keep past the end of current cycle.
    **/
    public void setFinal (Variable v, Type value)
    {
        // Note the change from writeIndex to readIndex.
        if (v.type instanceof Scalar) valuesFloat [v.readIndex] = (float) ((Scalar) value).value;
        else                          valuesObject[v.readIndex] = value;
    }

    public void init (Simulator simulator)
    {
    }

    public void integrate (Simulator simulator)
    {
    }

    public void update (Simulator simulator)
    {
    }

    /**
        Finalize the values of buffered variables, and complete any other housekeeping for current simulation cycle.
        @return true to remain in simulation queue. false to be removed from simulation.
    **/
    public boolean finish (Simulator simulator)
    {
        return true;
    }

    public void enqueue (Instance i)
    {
        i.next          = next;
        i.previous      = this;
        i.next.previous = i;
        i.previous.next = i;
    }

    public void dequeue ()
    {
        previous.next = next;
        next.previous = previous;
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

    public void dumpValues (boolean global, boolean temp)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        List<String> namesFloat;
        List<String> namesType;
        if (global)
        {
            if (temp)
            {
                namesFloat = bed.namesGlobalTempFloat;
                namesType  = bed.namesGlobalTempObject;
            }
            else
            {
                namesFloat = bed.namesGlobalFloat;
                namesType  = bed.namesGlobalObject;
            }
        }
        else  // local
        {
            if (temp)
            {
                namesFloat = bed.namesLocalTempFloat;
                namesType  = bed.namesLocalTempObject;
            }
            else
            {
                namesFloat = bed.namesLocalFloat;
                namesType  = bed.namesLocalObject;
            }
        }

        System.out.print ("[");
        if (valuesFloat != null)
        {
            for (int i = 0; i < valuesFloat.length; i++)
            {
                System.out.print (namesFloat.get (i) + "=");
                System.out.print (valuesFloat[i]);
                if (i < valuesFloat.length - 1) System.out.print (",");
            }
        }
        System.out.print ("][");
        if (valuesObject != null)
        {
            for (int i = 0; i < valuesObject.length; i++)
            {
                System.out.print (namesType.get (i) + "=");
                System.out.print (valuesObject[i]);
                if (i < valuesObject.length - 1) System.out.print (",");
            }
        }
        System.out.print ("]");
    }

    public boolean betterThan (Type that)
    {
        if (that instanceof Instance) return false;
        return true;
    }

    public String toString ()
    {
        if (equations == null) return "null@" + hashCode ();
        return equations.name + "@" + hashCode ();
    }

    /**
        Return a unique name for this instance within the simulation.
        Name consists of the equation set name combined with the index of this part,
        prefixed by the path to the parent part. In the case of a connection, the name
        consists of the path to each of the connected parts.
    **/
    public String path ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index == null) return "";  // because the name by itself adds no information; only parts with indices are useful in the path

        String result = equations.name + get (bed.index);

        Instance nextLevel = container.container;
        if (nextLevel == null  ||  nextLevel instanceof Wrapper) return result;
        String prefix = nextLevel.path ();
        if (prefix.isEmpty ()) return result;
        return prefix + "." + result;
    }

    public int compareTo (Type that)
    {
        if (that instanceof Instance) return hashCode () - that.hashCode ();
        return 1;  // We evaluate greater than any non-Instance
    }
}
