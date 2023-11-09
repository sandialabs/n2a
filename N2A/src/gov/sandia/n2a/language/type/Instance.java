/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.type;

import java.util.List;
import java.util.TreeSet;

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
import gov.sandia.n2a.plugins.extpoints.Backend;

/**
    Instance type. Represents an entire concrete object at simulation time.
**/
public class Instance extends Type
{
    public EquationSet equations;
    public Instance    container;
    public float[]     valuesFloat;  // memory is the premium resource, not accuracy, so use float rather than double
    public Object[]    valuesObject;

    public void allocate (int countFloat, int countObject)
    {
        if (countFloat  > 0) valuesFloat  = new float [countFloat];
        if (countObject > 0) valuesObject = new Object[countObject];
    }

    public interface Resolver
    {
        /**
            Given the current instance, determine the next instance in the resolution chain.
        **/
        public Instance resolve (Instance from);
        /**
            Indicates if resolution must pass through the instances of a population.
            For a direct reference, this is fatal.
            For a connection, this indicates that the collection of endpoints has a hierarchical structure.
        **/
        public boolean shouldEnumerate (Instance from);
    }

    public void resolve (TreeSet<VariableReference> references)
    {
        for (VariableReference r : references)
        {
            Instance result = this;
            Object last = r.resolution.get (r.resolution.size () - 1);
            for (Object o : r.resolution)
            {
                result = ((Resolver) o).resolve (result);
                if (o == last  &&  r.variable.global)  // Resolution is done and this is a global variable.
                {
                    if (result instanceof Part)
                    {
                        // Need to locate our population.
                        InternalBackendData bed = (InternalBackendData) result.equations.backendData;
                        result = (Instance) result.container.valuesObject[bed.populationIndex];
                    }
                }
                else
                {
                    if (result instanceof Population)
                    {
                        // Need to drill down to an instance of the population.
                        // This is only possible with a singleton.
                        InternalBackendData bed = (InternalBackendData) result.equations.backendData;
                        if (! bed.singleton)
                        {
                            Backend.err.get ().println ("ERROR: " + result.equations.prefix () + " is not a singleton, so a direct reference to its members is ambiguous.");
                            throw new Backend.AbortRun ();
                        }
                        result = (Instance) result.valuesObject[bed.instances];
                    }
                }
            }
            valuesObject[r.index] = result;
        }
    }

    /**
        Retrieves dt of the event we are attached to.
        Only works for Internal simulator objects like Part and Population.
    **/
    public double getDt ()
    {
        throw new EvaluationException ("Can't retrieve $t' for non-simulation object.");
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
        if (v.readIndex < 0)
        {
            if (v.readIndex == -2) return this;
            return v.type;
        }
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

    /**
        If this instance is on a simulation queue, then remove it.
        Note that only backend.internal.Part objects may be enqueued.
    **/
    public void dequeue ()
    {
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

    /**
        Sets any values touched by a zero-delay event as if they were set by finish()
        during the most recent sim cycle. This assumes nothing else has touched the buffered
        value other than event handler.
        @param v Implicitly, this is an "externalWrite" variable.
    **/
    public void finishEvent (Variable v)
    {
        Type current  = get (v);
        Type buffered = getFinal (v);
        switch (v.assignment)
        {
            case Variable.ADD:
                setFinal (v, current.add (buffered));
                set (v, v.type);
                break;
            case Variable.MULTIPLY:
            case Variable.DIVIDE:
                setFinal (v, current.multiply (buffered));
                if (v.type instanceof Matrix) set (v, ((Matrix) v.type).identity ());
                else                          set (v, new Scalar (1));
                break;
            case Variable.MIN:
                setFinal (v, current.min (buffered));
                if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.POSITIVE_INFINITY));
                else                          set (v, new Scalar (Double.POSITIVE_INFINITY));
                break;
            case Variable.MAX:
                setFinal (v, current.max (buffered));
                if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.NEGATIVE_INFINITY));
                else                          set (v, new Scalar (Double.NEGATIVE_INFINITY));
                break;
            default:  // REPLACE
                setFinal (v, buffered);
        }
    }

    public void clearExternalWriteBuffers (List<Variable> list)
    {
        // v.type should be pre-loaded with zero-equivalent values
        for (Variable v : list)
        {
            switch (v.assignment)
            {
                //case Variable.REPLACE: set (v, get (v)); break;
                //  finish() does the reverse move -- setFinal (v, getFinal (v)) -- so it already leaves
                //  the buffers in the desired condition. No need to repeat that work here. 
                //  OTOH, init() needs an extra line of code to ensure the same condition holds.
                case Variable.ADD:
                    set (v, v.type);  // initial value is zero-equivalent (additive identity)
                    break;
                case Variable.MULTIPLY:
                case Variable.DIVIDE:
                    // multiplicative identity
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).identity ());
                    else                          set (v, new Scalar (1));
                    break;
                case Variable.MIN:
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.POSITIVE_INFINITY));
                    else                          set (v, new Scalar (Double.POSITIVE_INFINITY));
                    break;
                case Variable.MAX:
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.NEGATIVE_INFINITY));
                    else                          set (v, new Scalar (Double.NEGATIVE_INFINITY));
                    break;
                // Must handle every assignment type. If any new ones are developed, add appropriate action here.
            }
        }
    }

    /**
        Prepare variables that require external buffering, but prepare their current value
        rather than buffered value, so that the init process can correctly set direct values.
    **/
    public void clearExternalWriteInit (List<Variable> list)
    {
        for (Variable v : list)
        {
            switch (v.assignment)
            {
                case Variable.ADD:
                    setFinal (v, v.type);
                    break;
                case Variable.MULTIPLY:
                case Variable.DIVIDE:
                    if (v.type instanceof Matrix) setFinal (v, ((Matrix) v.type).identity ());
                    else                          setFinal (v, new Scalar (1));
                    break;
                case Variable.MIN:
                    if (v.type instanceof Matrix) setFinal (v, ((Matrix) v.type).clear (Double.POSITIVE_INFINITY));
                    else                          setFinal (v, new Scalar (Double.POSITIVE_INFINITY));
                    break;
                case Variable.MAX:
                    if (v.type instanceof Matrix) setFinal (v, ((Matrix) v.type).clear (Double.NEGATIVE_INFINITY));
                    else                          setFinal (v, new Scalar (Double.NEGATIVE_INFINITY));
                    break;
            }
        }
    }

    /**
        Apply value during update cycle, using appropriate combiner.
    **/
    public void applyResult (Variable v, Type result)
    {
        if (v.assignment == Variable.REPLACE)
        {
            set (v, result);
        }
        else
        {
            // the rest of these require knowing the current value of the working result, which is most likely external buffered
            Type current = getFinal (v.reference);
            switch (v.assignment)
            {
                case Variable.ADD:      set (v, current.add      (result)); break;
                case Variable.MULTIPLY: set (v, current.multiply (result)); break;
                case Variable.DIVIDE:   set (v, current.divide   (result)); break;
                case Variable.MIN:      set (v, current.min      (result)); break;
                case Variable.MAX:      set (v, current.max      (result)); break;
            }
        }
    }

    /**
        Apply value during init cycle, using appropriate combiner.
    **/
    public void applyResultInit (Variable v, Type result)
    {
        if (v.assignment == Variable.REPLACE)
        {
            setFinal (v, result);
        }
        else
        {
            Type current = get (v.reference);
            switch (v.assignment)
            {
                case Variable.ADD:      setFinal (v, current.add      (result)); break;
                case Variable.MULTIPLY: setFinal (v, current.multiply (result)); break;
                case Variable.DIVIDE:   setFinal (v, current.divide   (result)); break;
                case Variable.MIN:      setFinal (v, current.min      (result)); break;
                case Variable.MAX:      setFinal (v, current.max      (result)); break;
            }
        }
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
        boolean top = container instanceof Wrapper  ||  container == null;  // In general, container is never null, as that would only happen if this is the instance of Wrapper.

        String result;
        if (top) result = "";
        else     result = equations.name;

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null  &&  ! (this instanceof Population)) result += get (bed.index);

        if (top) return result;
        String prefix = container.path ();
        if (prefix.isEmpty ()) return result;
        return prefix + "." + result;
    }

    public int compareTo (Type that)
    {
        if (that instanceof Instance) return hashCode () - that.hashCode ();
        return 1;  // We evaluate greater than any non-Instance
    }
}
