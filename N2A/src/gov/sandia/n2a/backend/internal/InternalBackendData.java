/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.DollarEvent;
import gov.sandia.n2a.language.type.Scalar;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class InternalBackendData
{
    public Object backendData;  ///< Other backends may use Internal as a preprocessor, and may need to store additional data not covered here.

    public List<Variable> localUpdate                  = new ArrayList<Variable> ();  // updated during regular call to update()
    public List<Variable> localInitRegular             = new ArrayList<Variable> ();  // non-$variables set by init()
    public List<Variable> localInitSpecial             = new ArrayList<Variable> ();  // $variables set by init()
    public List<Variable> localMembers                 = new ArrayList<Variable> ();  // stored inside the object
    public List<Variable> localBufferedRegular         = new ArrayList<Variable> ();  // non-$variables that need buffering (temporaries)
    public List<Variable> localBufferedSpecial         = new ArrayList<Variable> ();  // $variables that need buffering (temporaries)
    public List<Variable> localBufferedInternal        = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
    public List<Variable> localBufferedInternalUpdate  = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
    public List<Variable> localBufferedExternal        = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
    public List<Variable> localBufferedExternalWrite   = new ArrayList<Variable> ();  // subset of external that are due to external write
    public List<Variable> localIntegrated              = new ArrayList<Variable> ();  // store the result of integration of some other variable (the derivative)
    public List<Variable> globalUpdate                 = new ArrayList<Variable> ();
    public List<Variable> globalInit                   = new ArrayList<Variable> ();
    public List<Variable> globalMembers                = new ArrayList<Variable> ();
    public List<Variable> globalBuffered               = new ArrayList<Variable> ();
    public List<Variable> globalBufferedInternal       = new ArrayList<Variable> ();
    public List<Variable> globalBufferedInternalUpdate = new ArrayList<Variable> ();
    public List<Variable> globalBufferedExternal       = new ArrayList<Variable> ();
    public List<Variable> globalBufferedExternalWrite  = new ArrayList<Variable> ();
    public List<Variable> globalIntegrated             = new ArrayList<Variable> ();

    public TreeSet<VariableReference> localReference   = new TreeSet<VariableReference> (new ReferenceComparator ());
    public TreeSet<VariableReference> globalReference  = new TreeSet<VariableReference> (new ReferenceComparator ());

    // The following arrays have exactly the same order as EquationSet.connectionBindings
    // This includes the $variables in the next group below.
    // In all cases, there may be null entries if they are not applicable.
    public int[]      connectionTargets;
    public Variable[] accountableEndpoints;  ///< These are structured as direct members of the endpoint. No resolution. Instead, we use the Connection.endpoint array.

    public Variable   index;
    public Variable   init;
    public Variable[] k;
    public Variable   live;
    public Variable[] max;
    public Variable[] min;
    public Variable   n;
    public Variable   p;
    public Variable[] radius;
    public Variable   t;
    public Variable   dt;  // $t'
    public Variable   type;
    public Variable   xyz;

    // If the model uses events or otherwise has non-constant frequency, then we
    // may need to store last $t in order to calculate an accurate dt for integration.
    // Of course, this is only necessary if we actually have integrated variables.
    // If we must store $t, then lastT provides a handle into Instance.valuesFloat.
    // If we do not store $t, then this member is null.
    public Variable lastT;
    public boolean receivesEvents;  // TODO: use existence of event structures instead?

    public boolean storeDt;

    public boolean populationChangesWithoutN;
    public int     populationIndex;  // in container.populations

    public int liveStorage;
    public static final int LIVE_STORED   = 0;
    public static final int LIVE_ACCESSOR = 1;
    public static final int LIVE_CONSTANT = 2;

    // Size of storage blocks to allocate for part.
    // This is made more complicated by the fact that not everything is a simple float.
    // Some variables must be stored as objects. Thus there are two kinds of blocks.
    // Then there's temp versus permanent and local versus global, for 8 variants.
    public int countLocalTempFloat;
    public int countLocalTempType;
    public int countLocalFloat;
    public int countLocalType;
    public int countGlobalTempFloat;
    public int countGlobalTempType;
    public int countGlobalFloat;
    public int countGlobalType;

    // for debugging
    // We could use ArrayList.size() instead of the corresponding count* values above.
    public List<String> namesLocalTempFloat  = new ArrayList<String> ();
    public List<String> namesLocalTempType   = new ArrayList<String> ();
    public List<String> namesLocalFloat      = new ArrayList<String> ();
    public List<String> namesLocalType       = new ArrayList<String> ();
    public List<String> namesGlobalTempFloat = new ArrayList<String> ();
    public List<String> namesGlobalTempType  = new ArrayList<String> ();
    public List<String> namesGlobalFloat     = new ArrayList<String> ();
    public List<String> namesGlobalType      = new ArrayList<String> ();

    public Map<EquationSet,Conversion> conversions = new TreeMap<EquationSet,Conversion> ();  // maps from new part type to appropriate conversion record

    public class Conversion
    {
        // These two arrays are filled in parallel, such that index i in one matches i in the other.
        ArrayList<Variable> from = new ArrayList<Variable> ();
        ArrayList<Variable> to   = new ArrayList<Variable> ();
        int[] bindings;  // to index = bindings[from index]
    }

    class ReferenceComparator implements Comparator<VariableReference>
    {
        public int compare (VariableReference arg0, VariableReference arg1)
        {
            int count = arg0.resolution.size ();
            int result = count - arg1.resolution.size ();
            if (result != 0) return result;

            for (int i = 0; i < count; i++)
            {
                Object o0 = arg0.resolution.get (i);
                Object o1 = arg1.resolution.get (i);
                if (! o0.getClass ().equals (o1.getClass ())) return o0.getClass ().hashCode () - o1.getClass ().hashCode ();

                if (o0 instanceof EquationSet)
                {
                    result = ((EquationSet) o0).compareTo ((EquationSet) o1);
                    if (result != 0) return result;
                }
                else if (o0 instanceof Entry<?,?>)
                {
                    Entry<?,?> e0 = (Entry<?,?>) o0;
                    Entry<?,?> e1 = (Entry<?,?>) o1;
                    result = ((String     ) e0.getKey   ()).compareTo ((String     ) e1.getKey   ());
                    if (result != 0) return result;
                    result = ((EquationSet) e0.getValue ()).compareTo ((EquationSet) e1.getValue ());
                    if (result != 0) return result;
                }
            }

            return 0;
        }
    }

    public void analyze (EquationSet s)
    {
        System.out.println (s.name);
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            String className = "null";
            if (v.type != null) className = v.type.getClass ().getSimpleName ();
            System.out.println ("  " + v.nameString () + " " + v.attributeString () + " " + className);

            if      (v.name.equals ("$index")                  ) index = v;
            else if (v.name.equals ("$init" )                  ) init  = v;
            else if (v.name.equals ("$live" )                  ) live  = v;
            else if (v.name.equals ("$n"    )  &&  v.order == 0) n     = v;
            else if (v.name.equals ("$p"    )                  ) p     = v;
            else if (v.name.equals ("$type" )                  ) type  = v;
            else if (v.name.equals ("$xyz"  )                  ) xyz   = v;
            else if (v.name.equals ("$t"    ))
            {
                if (v.order == 0)
                {
                    t = v;
                }
                else if (v.order == 1)
                {
                    dt = v;
                    if (dt.hasUsers  &&  ! dt.hasAttribute ("initOnly"))
                    {
                        dt.removeAttribute ("preexistent");
                        storeDt = true;
                    }
                }
            }

            if (v.hasAttribute ("global"))
            {
                v.global = true;
                v.visit (new Visitor ()
                {
                    public boolean visit (Operator op)
                    {
                        if (op instanceof AccessVariable)
                        {
                            AccessVariable av = (AccessVariable) op;
                            if (av.reference.resolution.size () > 0)
                            {
                                if (globalReference.add (av.reference))
                                {
                                    av.reference.index = countGlobalType++;
                                    namesGlobalType.add ("reference to " + av.reference.variable.container.name);
                                }
                                else
                                {
                                    av.reference.index = globalReference.floor (av.reference).index;
                                }
                            }
                            return false;
                        }
                        return true;
                    }
                });
                if (! v.hasAny (new String[] {"constant", "accessor", "readOnly"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    boolean hasEquations = v.equations.size () > 0;
                    if (! initOnly  &&  hasEquations) globalUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        if (globalReference.add (v.reference))
                        {
                            v.reference.index = countGlobalType++;
                            namesGlobalType.add ("reference to " + v.reference.variable.container.name);
                        }
                        else
                        {
                            v.reference.index = globalReference.floor (v.reference).index;
                        }
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary) globalInit.add (v);
                        if (! temporary  &&  ! v.hasAttribute ("dummy"))
                        {
                            if (! v.hasAttribute ("preexistent")) globalMembers.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                globalBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  hasEquations  &&  ! initOnly))
                            {
                                external = true;
                                globalBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                globalBuffered.add (v);
                                if (! external)
                                {
                                    globalBufferedInternal.add (v);
                                    if (! initOnly) globalBufferedInternalUpdate.add (v);
                                }
                            }
                        }
                    }
                }
            }
            else  // local
            {
                v.visit (new Visitor ()
                {
                    public boolean visit (Operator op)
                    {
                        if (op instanceof AccessVariable)
                        {
                            AccessVariable av = (AccessVariable) op;
                            if (av.reference.resolution.size () > 0)
                            {
                                if (localReference.add (av.reference))
                                {
                                    av.reference.index = countLocalType++;
                                    namesLocalType.add ("reference to " + av.reference.variable.container.name);
                                }
                                else
                                {
                                    av.reference.index = localReference.floor (av.reference).index;
                                }
                            }
                            return false;
                        }
                        return true;
                    }
                });
                if (! v.hasAny (new String[] {"constant", "accessor", "readOnly"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    boolean hasEquations = v.equations.size () > 0;
                    if (v.derivative == null)
                    {
                        if (! initOnly  &&  hasEquations) localUpdate.add (v);
                    }
                    else  // has a derivative, so different rules apply
                    {
                        if (v.hasAttribute ("updates")) localUpdate.add (v);
                    }
                    if (v.hasAttribute ("reference"))
                    {
                        if (localReference.add (v.reference))
                        {
                            v.reference.index = countLocalType++;
                            namesLocalType.add ("reference to " + v.reference.variable.container.name);
                        }
                        else
                        {
                            v.reference.index = localReference.floor (v.reference).index;
                        }
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary)
                        {
                            if (v.name.startsWith ("$"))
                            {
                                if (! v.name.equals ("$index")  &&  ! v.name.equals ("$live")) localInitSpecial.add (v);
                            }
                            else
                            {
                                localInitRegular.add (v);
                            }
                        }
                        if (! temporary  &&  ! v.hasAttribute ("dummy"))
                        {
                            if (! v.hasAttribute ("preexistent")) localMembers.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                localBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  hasEquations  &&  ! initOnly))
                            {
                                external = true;
                                localBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                if (v.name.startsWith ("$")) localBufferedSpecial.add (v);
                                else                         localBufferedRegular.add (v);
                                if (! external)
                                {
                                    localBufferedInternal.add (v);
                                    if (! initOnly) localBufferedInternalUpdate.add (v);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Variable v : s.variables)  // we need these to be in order by differential level, not by dependency
        {
            if (v.derivative != null  &&  ! v.hasAny (new String[] {"constant", "initOnly"}))
            {
                if (v.hasAttribute ("global")) globalIntegrated.add (v);
                else                            localIntegrated.add (v);
            }
        }

        if (s.connectionBindings == null)  // compartment-specific stuff
        {
            populationIndex = 0;
            if (s.container != null  &&  s.container.parts != null)  // check for null specifically to guard against the Wrapper equation set (which is not fully constructed)
            {
                for (EquationSet p : s.container.parts)
                {
                    if (p == s) break;
                    populationIndex++;
                }
            }
        }
        else  // connection-specific stuff
        {
            int count = s.connectionBindings.size ();
            k      = new Variable[count];
            max    = new Variable[count];
            min    = new Variable[count];
            radius = new Variable[count];
            connectionTargets = new int[count];
            accountableEndpoints = new Variable[count];
            int i = 0;
            for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
            {
                String alias = c.getKey ();
                k     [i] = s.find (new Variable (alias + ".$k"     ));
                max   [i] = s.find (new Variable (alias + ".$max"   ));
                min   [i] = s.find (new Variable (alias + ".$min"   ));
                radius[i] = s.find (new Variable (alias + ".$radius"));

                if (min[i] != null  ||  max[i] != null)
                {
                    // Create a variable to wrap the count field in the target part
                    // The target part does not add this to its formal variables,
                    // nor do we resolve the variable in the target part. Rather, we
                    // keep a pointer to the target in the connection part and know how
                    // to directly access the count field.
                    InternalBackendData endpointBed = (InternalBackendData) c.getValue ().backendData;
                    Variable ae = new Variable ("");  // identity doesn't matter at this point
                    ae.type = new Scalar (0);
                    ae.readIndex = ae.writeIndex = endpointBed.countLocalFloat++;
                    namesLocalFloat.add ("$count");
                    accountableEndpoints[i] = ae;
                }

                int j = 0;
                for (EquationSet peer : s.container.parts)  // TODO: this assumes that all connections to peer populations under same container; need a more flexible way of locating target populations
                {
                    if (peer == c.getValue ())
                    {
                        connectionTargets[i] = j;
                        break;
                    }
                    j++;
                }

                i++;
            }
        }

        // TODO: Scan for events and set up structures
        // For now, just scan equations for $event() and set a flag
        class EventVisitor extends Visitor
        {
            public boolean found;
            public boolean visit (Operator op)
            {
                if (found) return false;
                if (op instanceof DollarEvent)
                {
                    found = true;
                    return false;
                }
                return true;
            }
        }
        EventVisitor eventVisitor = new EventVisitor ();
        for (Variable v : s.variables)
        {
            v.visit (eventVisitor);
            if (eventVisitor.found == true) break;
        }
        if (eventVisitor.found) receivesEvents = true;

        // Set index on variables
        // Initially readIndex = writeIndex = -1, and readTemp = writeTemp = false

        //   Locals
        for (Variable v : localMembers)
        {
            // If a float variable is a reference to another instance, we store a pointer to that instance
            // in the type array rather than the float array.
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.readIndex = v.writeIndex = countLocalFloat++;
                namesLocalFloat.add (v.nameString ());
            }
            else
            {
                v.readIndex = v.writeIndex = countLocalType++;
                namesLocalType.add (v.nameString ());
            }
        }
        for (Variable v : localBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = countLocalFloat++;
                namesLocalFloat.add ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = countLocalType++;
                namesLocalType.add ("next_" + v.nameString ());
            }
        }
        for (Variable v : localBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = countLocalTempFloat++;
                namesLocalTempFloat.add ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = countLocalTempType++;
                namesLocalTempType.add ("next_" + v.nameString ());
            }
        }

        //   Globals
        for (Variable v : globalMembers)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.readIndex = v.writeIndex = countGlobalFloat++;
                namesGlobalFloat.add (v.nameString ());
            }
            else
            {
                v.readIndex = v.writeIndex = countGlobalType++;
                namesGlobalType.add (v.nameString ());
            }
        }
        for (Variable v : globalBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = countGlobalFloat++;
                namesGlobalFloat.add ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = countGlobalType++;
                namesGlobalType.add ("next_" + v.nameString ());
            }
        }
        for (Variable v : globalBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = countGlobalTempFloat++;
                namesGlobalTempFloat.add ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = countGlobalTempType++;
                namesGlobalTempType.add ("next_" + v.nameString ());
            }
        }

        //   fully temporary values
        for (Variable v : s.variables)
        {
            if (! v.hasAttribute ("temporary")) continue;
            v.readTemp = v.writeTemp = true;
            if (v.hasAttribute ("global"))
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v)
                {
                    v.readIndex = v.writeIndex = countGlobalTempFloat++;
                    namesGlobalTempFloat.add (v.nameString ());
                }
                else
                {
                    v.readIndex = v.writeIndex = countGlobalTempType++;
                    namesGlobalTempType.add (v.nameString ());
                }
            }
            else
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v)
                {
                    v.readIndex = v.writeIndex = countLocalTempFloat++;
                    namesLocalTempFloat.add (v.nameString ());
                }
                else
                {
                    v.readIndex = v.writeIndex = countLocalTempType++;
                    namesLocalTempType.add (v.nameString ());
                }
            }
        }

        populationChangesWithoutN = s.lethalP  ||  s.lethalType  ||  s.canGrow ();
        if (populationChangesWithoutN  &&  (n == null  ||  n.hasAttribute ("constant")))
        {
            populationChangesWithoutN = false;  // suppress updates to $n, since it's not stored
            if (n != null  &&  n.hasUsers) System.err.println ("Warning: $n can change (due to structural dynamics) but it was detected as a constant. Equations that depend on $n may give incorrect results.");
        }

        if      (live.hasAttribute ("constant")) liveStorage = LIVE_CONSTANT;
        else if (live.hasAttribute ("accessor")) liveStorage = LIVE_ACCESSOR;
        else                                     liveStorage = LIVE_STORED;  // $live is "initOnly"

        translateReferences (s, localReference);
        translateReferences (s, globalReference);

        // Type conversions
        String [] forbiddenAttributes = new String [] {"global", "constant", "accessor", "reference", "temporary", "dummy", "preexistent"};
        for (EquationSet target : s.getConversionTargets ())
        {
            if (s.connectionBindings == null)
            {
                if (target.connectionBindings != null) throw new EvaluationException ("Can't change $type from compartment to connection.");
            }
            else
            {
                if (target.connectionBindings == null) throw new EvaluationException ("Can't change $type from connection to compartment.");
            }

            Conversion conversion = new Conversion ();
            conversions.put (target, conversion);

            // Match variables
            for (Variable v : target.variables)
            {
                if (v.name.equals ("$type")) continue;
                if (v.hasAny (forbiddenAttributes)) continue;
                Variable v2 = s.find (v);
                if (v2 != null  &&  v2.equals (v))
                {
                    conversion.to  .add (v);
                    conversion.from.add (v2);
                }
            }

            // Match connection bindings
            if (s.connectionBindings != null)  // Since we checked above, we know that target is also a connection.
            {
                conversion.bindings = new int[s.connectionBindings.size ()];
                int i = 0;
                for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
                {
                    String name = c.getKey ();
                    conversion.bindings[i] = -1;
                    int j = 0;
                    for (Entry<String, EquationSet> d : target.connectionBindings.entrySet ())
                    {
                        if (name.equals (d.getKey ()))
                        {
                            conversion.bindings[i] = j;
                            break;
                        }
                        j++;
                    }
                    // Note: ALL bindings must match. There is no other mechanism for initializing the endpoints.
                    if (conversion.bindings[i] < 0) throw new EvaluationException ("Unfulfilled connection binding during $type change.");
                    i++;
                }
            }

            // TODO: Match populations?
            // Currently, any contained populations do not carry over to new instance. Instead, it must create them from scratch.
        }
    }

    /**
        Determine if time of last integration must be stored.
        Note: global (population) variables are integrated at same time as container using its dt value.
        Thus, we only handle local variables here.
    **/
    public void analyzeLastT (EquationSet s)
    {
        boolean hasIntegrated = localIntegrated.size () > 0;
        for (EquationSet p : s.parts)
        {
            if (hasIntegrated) break;
            hasIntegrated = ((InternalBackendData) p.backendData).globalIntegrated.size () > 0;
        }
        boolean dtCanChange =  dt != null  &&  ! dt.hasAttribute ("initOnly");

        if (hasIntegrated  &&  (receivesEvents  ||  dtCanChange))
        {
            lastT = new Variable ("$lastT");
            lastT.readIndex = lastT.writeIndex = countLocalFloat++;
            namesLocalFloat.add (lastT.nameString ());
        }
    }

    /**
         Convert resolutions to a form that can be processed quickly at runtime.
         There are 3 ways to leave a part
         1) Ascend to its container
         2) Descend into a contained population -- need the index of population
         3) Go to a connected part -- need the index of the endpoint
         For simplicity, we only store a single integer.
         i <  0 -- select endpoint index -i-1
         i == 0 -- ascend to container
         i >  0 -- select population index i-1
    **/
    public void translateReferences (EquationSet s, TreeSet<VariableReference> references)
    {
        for (VariableReference r : references)
        {
            LinkedList<Object> newResolution = new LinkedList<Object> ();
            EquationSet current = s;
            Iterator<Object> it = r.resolution.iterator ();
            while (it.hasNext ())
            {
                Object o = it.next ();
                if (o instanceof EquationSet)  // We are following the containment hierarchy.
                {
                    EquationSet next = (EquationSet) o;
                    if (next == current.container)  // ascend to our container
                    {
                        newResolution.add (0);
                    }
                    else  // descend into one of our contained populations
                    {
                        if (! it.hasNext ()  &&  r.variable.hasAttribute ("global"))  // descend to the population object itself
                        {
                            int i = 1;
                            for (EquationSet p : current.parts)
                            {
                                if (p == next)
                                {
                                    newResolution.add (i);
                                    break;
                                }
                                i++;
                            }
                            if (i > current.parts.size ()) throw new EvaluationException ("Could not find population.");
                        }
                        else  // descend to an instance of the population.
                        {
                            throw new EvaluationException ("Can't reference into specific instance of a population.");
                        }
                    }
                    current = next;
                }
                else if (o instanceof Entry<?,?>)  // We are following a part reference (which means we are a connection)
                {
                    int i = 1;
                    for (Entry<String,EquationSet> c : current.connectionBindings.entrySet ())
                    {
                        if (c.equals (o))
                        {
                            newResolution.add (-i);
                            break;
                        }
                        i++;
                    }
                    if (i > current.connectionBindings.size ()) throw new EvaluationException ("Could not find connection.");
                    current = (EquationSet) ((Entry<?,?>) o).getValue ();
                }
            }
            r.resolution = newResolution;
        }
    }
}
