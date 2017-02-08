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
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.DollarEvent;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;

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
    // This includes the array $variables in the next group below.
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

    /**
        If the model uses events or otherwise has non-constant frequency, then we
        may need to store last $t in order to calculate an accurate dt for integration.
        Of course, this is only necessary if we actually have integrated variables.
        If we must store $t, then lastT provides a handle into Instance.valuesFloat.
        If we do not store $t, then this member is null.
    **/
    public Variable lastT;

    // Event structures
    public List<Integer>     eventLatches = new ArrayList<Integer> ();  // Indices within Instance.valuesFloat of each latch block. Generally, there will only be one, if any. Used to reset latches during finalize phase.
    public List<EventTarget> eventTargets = new ArrayList<EventTarget> ();
    public List<EventSource> eventSources = new ArrayList<EventSource> ();

    public boolean populationCanGrowOrDie;
    public boolean populationCanResize;
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
    public int countLocalTempObject;
    public int countLocalFloat;
    public int countLocalObject;
    public int countGlobalTempFloat;
    public int countGlobalTempObject;
    public int countGlobalFloat;
    public int countGlobalObject;

    // for debugging
    // We could use ArrayList.size() instead of the corresponding count* values above.
    public List<String> namesLocalTempFloat   = new ArrayList<String> ();
    public List<String> namesLocalTempObject  = new ArrayList<String> ();
    public List<String> namesLocalFloat       = new ArrayList<String> ();
    public List<String> namesLocalObject      = new ArrayList<String> ();
    public List<String> namesGlobalTempFloat  = new ArrayList<String> ();
    public List<String> namesGlobalTempObject = new ArrayList<String> ();
    public List<String> namesGlobalFloat      = new ArrayList<String> ();
    public List<String> namesGlobalObject     = new ArrayList<String> ();

    public Map<EquationSet,Conversion> conversions = new TreeMap<EquationSet,Conversion> ();  // maps from new part type to appropriate conversion record

    public class Conversion
    {
        // These two arrays are filled in parallel, such that index i in one matches i in the other.
        public ArrayList<Variable> from = new ArrayList<Variable> ();
        public ArrayList<Variable> to   = new ArrayList<Variable> ();
        public int[] bindings;  // to index = bindings[from index]
    }

    public class EventTarget
    {
        public DollarEvent event;       // For evaluating whether the event should be triggered. There may be several equivalent $event() calls in the part, so this is just one representative of the group.
        public int         valueIndex;  // position of bit array in valuesFloat
        public int         mask;        // an unsigned AND between this and the (int cast) entry from valuesFloat will indicate event active
        public boolean     testAll;     // indicates that the monitor must test every event target of this type separately, generally because the trigger references variables outside the source part
        public int         edge  = RISE;
        public double      delay = -1;  // default is no-care; Indicates to process event in next regularly scheduled cycle of the target part
        public Map<EquationSet,EventSource> sources = new TreeMap<EquationSet,EventSource> ();
        public List<Variable> dependencies = new ArrayList<Variable> ();

        /**
            Every $event() function has a trigger expression as its first parameter.
            This expression is tested during the finish phase of any monitored parts,
            which are generally different from $event()'s home part. The home part
            will keep an auxiliary variable which $event() updates each time it is
            tested. However, if the expression is a simple variable, then we compare the
            variable's buffered value instead. The variable is likely to be a reference
            to the monitored part (in which case the buffered value is stored there).
            In either case, we need the identity of the variable.
        **/
        public Variable track;
        public boolean  trackOne;  // we are following a single first-class variable, so track is only a holder for its reference

        // edge types
        public static final int EVALUATE = -1;  // Always recompute the edge type, because the parameter is not constant.
        public static final int RISE     = 0;
        public static final int FALL     = 1;
        public static final int CHANGE   = 2;

        public EventTarget (DollarEvent event)
        {
            this.event = event;
        }

        public void setLatch (Instance i)
        {
            i.valuesFloat[valueIndex] = Float.intBitsToFloat (Float.floatToRawIntBits (i.valuesFloat[valueIndex]) | mask);
        }

        public void clearLatch (Instance i)
        {
            i.valuesFloat[valueIndex] = Float.intBitsToFloat (Float.floatToRawIntBits (i.valuesFloat[valueIndex]) & ~mask);
        }

        public boolean getLatch (Instance i)
        {
            return (Float.floatToRawIntBits (i.valuesFloat[valueIndex]) & mask) != 0;
        }

        /**
            Determine if this event should be triggered.
            Must be called during the finish phase, before buffered values are written to their primary storage.
            @param targetPart Must be an instance of the part where the $event() function appears,
            even if it is called during update of another part.
            @return -2 if this event did not fire. -1 if it fired with no-care delivery.
            0 or greater if it fired and we specify the delay until delivery.
        **/
        public double test (Instance targetPart, Simulator simulator)
        {
            // Evaluate any temporaries needed by operands in $event()
            InstanceTemporaries temp = new InstanceTemporaries (targetPart, simulator, false);
            for (Variable v : dependencies)
            {
                Type result = v.eval (temp);
                if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
            }

            double before = ((Scalar) temp.get (track.reference)).value;
            double after;
            if (trackOne)  // This is a single variable, so check its value directly.
            {
                after = ((Scalar) temp.getFinal (track.reference)).value;
            }
            else  // This is an expression, so use our private auxiliary variable.
            {
                Scalar result = (Scalar) event.operands[0].eval (temp);
                temp.setFinal (track, result);  // Since the variable is effectively hidden, we don't wait for the finalize phase.
                after = result.value;
            }

            int edge = this.edge;
            if (edge == EVALUATE)
            {
                Text t = (Text) event.operands[2].eval (temp);
                if      (t.value.equalsIgnoreCase ("change")) edge = CHANGE;
                else if (t.value.equalsIgnoreCase ("fall"  )) edge = FALL;
                else                                          edge = RISE;
            }
            switch (edge)
            {
                case CHANGE:
                    if (before == after) return -2;
                    break;
                case FALL:
                    if (before == 0  ||  after != 0) return -2;
                    break;
                case RISE:
                default:
                    if (after == 0  ||  before != 0) return -2;
            }

            if (delay >= -1) return delay;
            double result = ((Scalar) event.operands[1].eval (temp)).value;
            if (result < 0) result = -1;
            return result;
        }

        /**
            Similar to test(), but assumes the event will fire, and simply computes the requested delay.
        **/
        public double delay (Instance targetPart, Simulator simulator)
        {
            InstanceTemporaries temp = new InstanceTemporaries (targetPart, simulator, false);
            for (Variable v : dependencies)
            {
                Type result = v.eval (temp);
                if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
            }
            return ((Scalar) event.operands[1].eval (temp)).value;
        }

        public boolean equals (Object that)
        {
            return event.compareTo (((EventTarget) that).event) == 0;
        }
    }

    public class EventSource
    {
        public EventTarget       target;
        public int               monitorIndex; // position of monitor array in source_instance.valuesObject
        public VariableReference reference;    // position of reference to source part in target_instance.valuesObject
    }

    public class ReferenceComparator implements Comparator<VariableReference>
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

    /**
        Find $event() calls and collate them (in case the same signature appears several different places
        in the equation set).
        This must be done before the variables are sorted into sets according to attributes, because we
        may need to add the "externalRead" attribute to some of them.
    **/
    public void analyzeEvents (final EquationSet s) throws Backend.AbortRun
    {
        class EventVisitor extends Visitor
        {
            public int valueIndex = -1;
            public int mask;
            public boolean exception = false;

            public boolean visit (Operator op)
            {
                if (op instanceof DollarEvent)
                {
                    DollarEvent de = (DollarEvent) op;
                    if (de.eventType == null)  // this $event has not yet been analyzed
                    {
                        final EventTarget et = new EventTarget (de);
                        int targetIndex = eventTargets.indexOf (et);
                        if (targetIndex >= 0)  // event target already exists
                        {
                            de.eventType = eventTargets.get (targetIndex);
                        }
                        else  // we must create a new event target, or more properly, fill in the event target we just used as a query object
                        {
                            // Create an entry and save the index
                            eventTargets.add (et);
                            de.eventType = et;

                            // Allocate a latch bit
                            if (valueIndex == -1)
                            {
                                valueIndex = countLocalFloat++;
                                namesLocalFloat.add ("$event_latch" + valueIndex);
                                eventLatches.add (valueIndex);
                                mask = 1;
                            }
                            et.valueIndex = valueIndex;
                            et.mask       = mask;
                            mask <<= 1;
                            if (mask > 0x20000000) valueIndex = -1;  // Allocate another float when we declare over 30 events ... but who would ever need that?

                            // Analyze the $event
                            //   edge type
                            if (de.operands.length < 3)
                            {
                                et.edge = EventTarget.RISE;
                            }
                            else if (de.operands[2] instanceof Constant)
                            {
                                Constant c = (Constant) de.operands[2];
                                if (c.value instanceof Text)
                                {
                                    Text t = (Text) c.value;
                                    if      (t.value.equalsIgnoreCase ("change")) et.edge = EventTarget.CHANGE;
                                    else if (t.value.equalsIgnoreCase ("fall"  )) et.edge = EventTarget.FALL;
                                    else                                          et.edge = EventTarget.RISE;
                                }
                                else
                                {
                                    throw new EvaluationException ("Edge type for $event() must be specified with a string.");
                                }
                            }
                            else
                            {
                                et.edge = EventTarget.EVALUATE;
                            }

                            //   delay
                            //   already initialized: et.delay = -1
                            if (de.operands.length >= 2)
                            {
                                if (de.operands[1] instanceof Constant)
                                {
                                    Constant c = (Constant) de.operands[1];
                                    et.delay = ((Scalar) c.value).value;
                                    if (et.delay < 0) et.delay = -1;
                                }
                                else
                                {
                                    et.delay = -2;  // indicates that we need to evaluate delay at run time
                                }
                            }

                            //   auxiliary variable
                            if (de.operands[0] instanceof AccessVariable)
                            {
                                AccessVariable av = (AccessVariable) de.operands[0];
                                VariableReference reference = av.reference;
                                Variable v = reference.variable;
                                if (v.hasAttribute ("temporary"))
                                {
                                    // Treat temporaries like expressions (ie: create an auxiliary variable to track changes in its value),
                                    // so fall through to the !trackOne case below.
                                    // However, if this is a temporary in the monitored part, and the monitored part is not the home part,
                                    // then the user has broken the rule that we can't see temporaries in other parts.
                                    if (v.container != s)
                                    {
                                        Backend.err.get ().println ("ERROR: Attempt to reference a temporary in an external part: " + v.container.name + "." + v.nameString () + " from " + s.name);
                                        exception = true;
                                    }
                                }
                                else
                                {
                                    v.addAttribute ("externalRead");  // ensure it's buffered, so we can detect change
                                    et.trackOne = true;
                                    et.track = new Variable ();  // just a holder for the reference
                                    et.track.reference = reference;
                                }
                            }
                            if (! et.trackOne)  // expression, so create auxiliary variable
                            {
                                et.track = new Variable ("$event_aux" + eventTargets.size (), 0);
                                et.track.type = new Scalar (0);
                                et.track.reference = new VariableReference ();
                                et.track.reference.variable = et.track;
                                et.track.readIndex = et.track.writeIndex = countLocalFloat++;
                                namesLocalFloat.add (et.track.name);
                            }

                            //   locate any temporaries for evaluation
                            //     Tie into the dependency graph using a phantom variable (which can go away afterward without damaging the graph).
                            final Variable phantom = new Variable ("$event");
                            phantom.uses = new ArrayList<Variable> ();
                            for (int i = 0; i < et.event.operands.length; i++) et.event.operands[i].visit (new Visitor ()
                            {
                                public boolean visit (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        Variable v = av.reference.variable;
                                        if (! phantom.uses.contains (v)) phantom.uses.add (v);
                                        return false;
                                    }
                                    return true;
                                }
                            });
                            //     Scan all variables in equation set to see if we need them
                            for (Variable t : s.variables)
                            {
                                if (t.hasAttribute ("temporary")  &&  phantom.dependsOn (t) != null) et.dependencies.add (t);
                            }

                            //   set up monitors in source parts
                            //   and determine if monitor needs to test every target, or if one representative target is sufficient
                            class ReferenceVisitor extends Visitor
                            {
                                TreeSet<EquationSet> uniqueContainers = new TreeSet<EquationSet> ();
                                public boolean visit (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        EquationSet container = av.reference.variable.container;
                                        uniqueContainers.add (container);  // we could add the target part as one of the containers, if in fact we use local variables in the trigger expression
                                        if (container != s  &&  ! et.sources.containsKey (container))  // external reference, so we need to plug a monitor into the other part
                                        {
                                            InternalBackendData containerBed = (InternalBackendData) container.backendData;
                                            EventSource es = new EventSource ();
                                            es.target       = et;
                                            es.monitorIndex = containerBed.countLocalObject++;
                                            es.reference    = av.reference;
                                            containerBed.namesLocalObject.add ("$event_monitor_" + s.prefix ());
                                            containerBed.eventSources.add (es);
                                            et.sources.put (container, es);
                                        }
                                        return false;
                                    }
                                    return true;
                                }
                            }
                            ReferenceVisitor rv = new ReferenceVisitor ();
                            de.operands[0].visit (rv);
                            // If all the variables used by the trigger expression are within the same source
                            // part, then the answer will be the same for all registered target parts. However,
                            // if any of the variables belong to a different source part or to the target part
                            // itself, then the answer could vary, and every target must be tested separately.
                            if (rv.uniqueContainers.size () > 1) et.testAll = true;
                        }
                    }
                }
                return true;
            }
        }

        EventVisitor eventVisitor = new EventVisitor ();
        for (Variable v : s.variables)
        {
            v.visit (eventVisitor);
        }
        if (eventVisitor.exception) throw new Backend.AbortRun ();
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
            else if (v.name.equals ("$p"    )  &&  v.order == 0) p     = v;
            else if (v.name.equals ("$type" )                  ) type  = v;
            else if (v.name.equals ("$xyz"  )  &&  v.order == 0) xyz   = v;
            else if (v.name.equals ("$t"    ))
            {
                if      (v.order == 0) t  = v;
                else if (v.order == 1) dt = v;
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
                                    av.reference.index = countGlobalObject++;
                                    namesGlobalObject.add ("reference to " + av.reference.variable.container.name);
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
                if (! v.hasAny (new String[] {"constant", "accessor", "readOnly"})  ||  v.hasAll (new String[] {"constant", "reference"}))  // eliminate non-computed values, unless they refer to a variable outside the immediate equation set
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    boolean updates = ! initOnly  &&  v.equations.size () > 0  &&  (v.derivative == null  ||  v.hasAttribute ("updates"));
                    if (updates) globalUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        if (globalReference.add (v.reference))
                        {
                            v.reference.index = countGlobalObject++;
                            namesGlobalObject.add ("reference to " + v.reference.variable.container.name);
                        }
                        else
                        {
                            v.reference.index = globalReference.floor (v.reference).index;
                        }
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        if (! temporary  ||  v.hasUsers ()) globalInit.add (v);
                        if (! temporary  &&  ! v.hasAttribute ("dummy"))
                        {
                            if (! v.hasAttribute ("preexistent")) globalMembers.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                globalBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  updates))
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
                                    av.reference.index = countLocalObject++;
                                    namesLocalObject.add ("reference to " + av.reference.variable.container.name);
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
                if (! v.hasAny (new String[] {"constant", "accessor", "readOnly"})  ||  v.hasAll (new String[] {"constant", "reference"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    boolean updates = ! initOnly  &&  v.equations.size () > 0  &&  (v.derivative == null  ||  v.hasAttribute ("updates"));
                    if (updates) localUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        if (localReference.add (v.reference))
                        {
                            v.reference.index = countLocalObject++;
                            namesLocalObject.add ("reference to " + v.reference.variable.container.name);
                        }
                        else
                        {
                            v.reference.index = localReference.floor (v.reference).index;
                        }
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        if (! temporary  ||  v.hasUsers ())
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
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  updates))
                            {
                                external = true;
                                localBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                if (v.name.startsWith ("$")) localBufferedSpecial.add (v);
                                else                         localBufferedRegular.add (v);
                                if (! external)  // v got here only by being a "cycle", not "externalRead" or "externalWrite"
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
                v.readIndex = v.writeIndex = countLocalObject++;
                namesLocalObject.add (v.nameString ());
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
                v.writeIndex = countLocalObject++;
                namesLocalObject.add ("next_" + v.nameString ());
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
                v.writeIndex = countLocalTempObject++;
                namesLocalTempObject.add ("next_" + v.nameString ());
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
                v.readIndex = v.writeIndex = countGlobalObject++;
                namesGlobalObject.add (v.nameString ());
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
                v.writeIndex = countGlobalObject++;
                namesGlobalObject.add ("next_" + v.nameString ());
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
                v.writeIndex = countGlobalTempObject++;
                namesGlobalTempObject.add ("next_" + v.nameString ());
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
                    v.readIndex = v.writeIndex = countGlobalTempObject++;
                    namesGlobalTempObject.add (v.nameString ());
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
                    v.readIndex = v.writeIndex = countLocalTempObject++;
                    namesLocalTempObject.add (v.nameString ());
                }
            }
        }

        populationCanGrowOrDie =  s.lethalP  ||  s.lethalType  ||  s.canGrow ();
        if (n != null)
        {
            populationCanResize = globalMembers.contains (n);
            if (populationCanGrowOrDie  &&  ! populationCanResize  &&  n.hasUsers ())
            {
                // TODO: if $n has users and populationCanGrowOrDie, then $n should not be tagged "constant"
                System.err.println ("WARNING: $n can change (due to structural dynamics) but it was detected as a constant. Equations that depend on $n may give incorrect results.");
            }
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

        if (hasIntegrated  &&  (eventTargets.size () > 0  ||  dtCanChange))
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

    public void dump ()
    {
        dumpVariableList ("localUpdate                 ", localUpdate);
        dumpVariableList ("localInitRegular            ", localInitRegular);
        dumpVariableList ("localInitSpecial            ", localInitSpecial);
        dumpVariableList ("localMembers                ", localMembers);
        dumpVariableList ("localBufferedRegular        ", localBufferedRegular);
        dumpVariableList ("localBufferedSpecial        ", localBufferedSpecial);
        dumpVariableList ("localBufferedInternal       ", localBufferedInternal);
        dumpVariableList ("localBufferedInternalUpdate ", localBufferedInternalUpdate);
        dumpVariableList ("localBufferedExternal       ", localBufferedExternal);
        dumpVariableList ("localBufferedExternalWrite  ", localBufferedExternalWrite);
        dumpVariableList ("localIntegrated             ", localIntegrated);

        dumpVariableList ("globalUpdate                ", globalUpdate);
        dumpVariableList ("globalInit                  ", globalInit);
        dumpVariableList ("globalMembers               ", globalMembers);
        dumpVariableList ("globalBuffered              ", globalBuffered);
        dumpVariableList ("globalBufferedInternal      ", globalBufferedInternal);
        dumpVariableList ("globalBufferedInternalUpdate", globalBufferedInternalUpdate);
        dumpVariableList ("globalBufferedExternal      ", globalBufferedExternal);
        dumpVariableList ("globalBufferedExternalWrite ", globalBufferedExternalWrite);
        dumpVariableList ("globalIntegrated            ", globalIntegrated);

        dumpReferenceSet ("localReference", localReference);
        dumpReferenceSet ("globalReference", globalReference);
    }

    public void dumpVariableList (String name, List<Variable> list)
    {
        System.out.print ("  " + name + ":");
        for (Variable v : list) System.out.print (" " + v.nameString ());
        System.out.println ();
    }

    public void dumpReferenceSet (String name, TreeSet<VariableReference> set)
    {
        System.out.println ("  " + name);
        for (VariableReference r : set) System.out.println ("    " + r.variable.nameString () + " in " + r.variable.container.name);
    }
}
