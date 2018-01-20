/*
Copyright 2015-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.AccountableConnection;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.util.Comparator;
import java.util.IdentityHashMap;
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

    public ReferenceComparator referenceComparator = new ReferenceComparator ();
    public TreeSet<VariableReference> localReference   = new TreeSet<VariableReference> (referenceComparator);
    public TreeSet<VariableReference> globalReference  = new TreeSet<VariableReference> (referenceComparator);

    // The following arrays have exactly the same order as EquationSet.connectionBindings
    public int      endpoints;           // Position in valuesObject of first reference to a connected instance. References are allocated as a contiguous block.
    public int[]    count;               // Position in endpoint.valuesFloat of count value for this connection
    public Object[] projectDependencies; // References used within a $project expression
    public Object[] projectReferences;   // References used within a $project expression

    // Ready-to-use handles for common $variables
    // Arrays are associated with connectionBindings, as above. Elements may be null if not applicable.
    public Variable   index;
    public Variable   init;
    public Variable[] k;
    public Variable   live;
    public Variable[] max;
    public Variable[] min;
    public Variable   n;
    public Variable   p;
    public Variable[] project;
    public Variable[] radius;
    public Variable   t;
    public Variable   dt;  // $t'
    public Variable   type;
    public Variable   xyz;

    public List<Variable> Pdependencies;  // Contains any temporary variables (in evaluation order) that $p depends on. Guaranteed non-null if $p is non-null.

    /**
        If the model uses events or otherwise has non-constant frequency, then we
        may need to store last $t in order to calculate an accurate dt for integration.
        Of course, this is only necessary if we actually have integrated variables.
        If we must store $t, then lastT provides a handle into Instance.valuesFloat.
        If we do not store $t, then this member is null.
    **/
    public Variable lastT;

    // Event structures
    public List<Integer>     eventLatches    = new ArrayList<Integer> ();  // Indices within Instance.valuesFloat of each latch block. Generally, there will only be one, if any. Used to reset latches during finalize phase.
    public List<EventTarget> eventTargets    = new ArrayList<EventTarget> ();
    public List<EventSource> eventSources    = new ArrayList<EventSource> ();
    public List<Variable>    eventReferences = new ArrayList<Variable> ();  // Variables in referenced parts that need to be finalized when this part executes due to a zero-delay event.

    public boolean populationCanGrowOrDie;  // by structural dynamics other than $n
    public boolean populationCanResize;     // by manipulating $n
    public int     populationIndex;         // in container.populations

    public int indexNext;      // position in population valuesFloat of index counter
    public int indexAvailable; // position in population valuesObject of list of dead indices
    public int instances = -1; // position in population valuesObject of instances list; -1 means don't track instances
    public int firstborn;      // position in population valuesFloat of index of first newborn instance for current cycle
    public int newborn;        // position in instance valuesFloat of newborn flag

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
        public Event event;          // For evaluating whether the event should be triggered. There may be several equivalent event() calls in the part, so this is just one representative of the group.
        public int         valueIndex;     // position of bit array in valuesFloat
        public int         mask;           // an unsigned AND between this and the (int cast) entry from valuesFloat will indicate event active
        public int         edge  = RISE;
        public double      delay = -1;     // default is no-care; Indicates to process event in next regularly scheduled cycle of the target part
        public int         timeIndex = -1; // position in valuesFloat of timestamp when last event to this target was generated; used to force multiple sources to generate only one event in a given cycle; -1 means the guard is unneeded
        public Map<EquationSet,EventSource> sources = new TreeMap<EquationSet,EventSource> ();
        public List<Variable> dependencies = new ArrayList<Variable> ();

        /**
            Every event() function has a trigger expression as its first parameter.
            This expression is tested during the finish phase of any monitored parts,
            which are generally different from event()'s home part. The home part
            will keep an auxiliary variable which event() updates each time it is
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
        public static final int NONZERO  = 3;

        public EventTarget (Event event)
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
            @param targetPart Must be an instance of the part where the event() function appears,
            even if it is called during update of another part.
            @return -2 if this event did not fire. -1 if it fired with no-care delivery.
            0 or greater if it fired and we specify the delay until delivery.
        **/
        public double test (Instance targetPart, Simulator simulator)
        {
            // Evaluate any temporaries needed by operands in event()
            InstanceTemporaries temp = new InstanceTemporaries (targetPart, simulator, false);
            for (Variable v : dependencies)
            {
                Type result = v.eval (temp);
                if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
            }

            double before;
            if (this.edge == NONZERO) before = 0;  // just to silence error about uninitialized variable
            else                      before = ((Scalar) temp.get (track.reference)).value;

            double after;
            if (trackOne)  // This is a single variable, so check its value directly.
            {
                after = ((Scalar) temp.getFinal (track.reference)).value;
            }
            else  // This is an expression, so use our private auxiliary variable.
            {
                Scalar result = (Scalar) event.operands[0].eval (temp);
                if (this.edge != NONZERO) temp.setFinal (track, result);  // Since the variable is effectively hidden, we don't wait for the finalize phase.
                after = result.value;
            }

            int edge = this.edge;
            if (edge == EVALUATE)
            {
                Text t = (Text) event.operands[2].eval (temp);
                if      (t.value.equalsIgnoreCase ("nonzero")) edge = NONZERO;
                else if (t.value.equalsIgnoreCase ("change" )) edge = CHANGE;
                else if (t.value.equalsIgnoreCase ("fall"   )) edge = FALL;
                else                                           edge = RISE;
            }
            switch (edge)
            {
                case NONZERO:
                    if (after == 0) return -2;
                    // Guard against multiple events in a given cycle.
                    // Note that other trigger types don't need this because they set the auxiliary variable,
                    // so the next test in the same cycle will no longer see change.
                    if (timeIndex >= 0)
                    {
                        float moduloTime = (float) Math.IEEEremainder (simulator.currentEvent.t, 1);  // Wrap time at 1 second, to fit in float precision.
                        if (targetPart.valuesFloat[timeIndex] == moduloTime) return -2;
                        targetPart.valuesFloat[timeIndex] = moduloTime;
                    }
                    break;
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

            if (delay >= -1) return delay;  // constant delay, which is either -1 (no care), 0 or greater
            // otherwise, evaluate delay
            double result = ((Scalar) event.operands[1].eval (temp)).value;
            if (result < 0) return -1;  // force any negative value to be exactly -1 (no care)
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
            double result = ((Scalar) event.operands[1].eval (temp)).value;
            if (result < 0) return -1;
            return result;
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
        public VariableReference reference;    // for determining index of source part in target_instance.valuesObject. This is done indirectly so that event analysis can be done before indices are fully assigned.
        public boolean           testEach;     // indicates that the monitor must test each target instance, generally because the trigger references variables outside the source part
        public boolean           delayEach;    // indicates that the monitor evaluate the delay for each target instance
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
                else if (o0 instanceof ConnectionBinding)
                {
                    ConnectionBinding c0 = (ConnectionBinding) o0;
                    ConnectionBinding c1 = (ConnectionBinding) o1;
                    result = c0.alias.compareTo (c1.alias);
                    if (result != 0) return result;
                    result = c0.endpoint.compareTo (c1.endpoint);
                    if (result != 0) return result;
                }
            }

            return 0;
        }
    }

    public InternalBackendData (EquationSet s)
    {
        // Allocate space for populations before anything else has a chance.
        // All other code assumes populations are the first entries in valuesObject.
        for (EquationSet p : s.parts)
        {
            countLocalObject++;
            namesLocalObject.add (p.name);
        }
    }

    /**
        Find event() calls and collate them (in case the same signature appears several different places
        in the equation set).
        This must be done before the variables are sorted into sets according to attributes, because we
        may need to add the "externalRead" attribute to some of them.
    **/
    public void analyzeEvents (final EquationSet s) throws Backend.AbortRun
    {
        class EventVisitor extends Visitor
        {
            public int     valueIndex = -1;
            public int     mask;
            public boolean found;

            public boolean visit (Operator op)
            {
                if (op instanceof Event)
                {
                    found = true;
                    Event de = (Event) op;
                    if (de.eventType == null)  // this event has not yet been analyzed
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
                                namesLocalFloat.add ("event_latch" + valueIndex);
                                eventLatches.add (valueIndex);
                                mask = 1;
                            }
                            et.valueIndex = valueIndex;
                            et.mask       = mask;
                            mask <<= 1;
                            if (mask > 0x400000) valueIndex = -1;  // Due to limitations of float-int conversion, only 23 bits are available. Allocate another float.

                            
                            // Analyze the event ...

                            // Determine edge type
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
                                    if      (t.value.equalsIgnoreCase ("nonzero")) et.edge = EventTarget.NONZERO;
                                    else if (t.value.equalsIgnoreCase ("change" )) et.edge = EventTarget.CHANGE;
                                    else if (t.value.equalsIgnoreCase ("fall"   )) et.edge = EventTarget.FALL;
                                    else                                           et.edge = EventTarget.RISE;
                                }
                                else
                                {
                                    Backend.err.get ().println ("ERROR: Edge type for event() must be specified with a string.");
                                    throw new Backend.AbortRun ();
                                }
                            }
                            else
                            {
                                et.edge = EventTarget.EVALUATE;
                            }

                            // Allocate auxiliary variable
                            if (de.operands[0] instanceof AccessVariable)
                            {
                                AccessVariable av = (AccessVariable) de.operands[0];
                                VariableReference reference = av.reference;
                                Variable v = reference.variable;

                                // If this is a temporary in the monitored part, and the monitored part is not the home part,
                                // then the user has broken the rule that we can't see temporaries in other parts.
                                if (v.hasAttribute ("temporary")  &&  v.container != s)
                                {
                                    Backend.err.get ().println ("WARNING: Cannot be temporary due to event monitor: " + v.container.name + "." + v.nameString () + " from " + s.name);
                                    v.removeAttribute ("temporary");
                                }

                                // Treat temporaries like expressions (ie: create an auxiliary variable to track changes in its value),
                                // so fall through to the !trackOne case below.
                                if (! v.hasAttribute ("temporary"))
                                {
                                    v.addAttribute ("externalRead");  // ensure it's buffered, so we can detect change
                                    et.trackOne = true;
                                    et.track = new Variable ();  // just a holder for the reference
                                    et.track.reference = reference;
                                }
                            }
                            if (! et.trackOne  &&  et.edge != EventTarget.NONZERO)  // Expression, so create auxiliary variable. Aux not needed for NONZERO, because no change detection.
                            {
                                et.track = new Variable ("event_aux" + eventTargets.size (), 0);
                                et.track.type = new Scalar (0);
                                et.track.reference = new VariableReference ();
                                et.track.reference.variable = et.track;
                                et.track.readIndex = et.track.writeIndex = countLocalFloat++;
                                namesLocalFloat.add (et.track.name);
                            }

                            // Locate any temporaries for evaluation. TODO: for more efficiency, we could have separate lists of temporaries for the condition and delay operands
                            //   Tie into the dependency graph using a phantom variable (which can go away afterward without damaging the graph).
                            final Variable phantom = new Variable ("event");
                            phantom.uses = new IdentityHashMap<Variable,Integer> ();
                            for (int i = 0; i < et.event.operands.length; i++) et.event.operands[i].visit (new Visitor ()
                            {
                                public boolean visit (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        Variable v = av.reference.variable;
                                        if (! phantom.uses.containsKey (v)) phantom.uses.put (v, 1);
                                        return false;
                                    }
                                    return true;
                                }
                            });
                            //   Scan all variables in equation set to see if we need them
                            for (Variable t : s.variables)
                            {
                                if (t.hasAttribute ("temporary")  &&  phantom.dependsOn (t) != null) et.dependencies.add (t);
                            }

                            // Delay
                            // Note the default is already set to -1 (no care)
                            class DelayVisitor extends Visitor
                            {
                                TreeSet<EquationSet> containers = new TreeSet<EquationSet> ();
                                public boolean visit (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        containers.add (av.reference.variable.container);  // could include the target part itself, if in fact we use local variables
                                        return false;
                                    }
                                    return true;
                                }
                            }
                            DelayVisitor dv = new DelayVisitor ();
                            if (de.operands.length >= 2)
                            {
                                if (de.operands[1] instanceof Constant)
                                {
                                    Constant c = (Constant) de.operands[1];
                                    et.delay = (float) ((Scalar) c.value).value;
                                    if (et.delay < 0) et.delay = -1;
                                }
                                else
                                {
                                    et.delay = -2;  // indicates that we need to evaluate delay at run time
                                    de.operands[1].visit (dv);
                                }
                            }

                            // Set up monitors in source parts
                            class ConditionVisitor extends Visitor
                            {
                                TreeSet<EquationSet> containers = new TreeSet<EquationSet> ();
                                public boolean visit (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        Variable v = av.reference.variable;
                                        EquationSet sourceContainer = v.container;
                                        containers.add (sourceContainer);

                                        // Set up monitors for values that can vary during update.
                                        if (! (v.hasAttribute ("constant")  ||  v.hasAttribute ("initOnly")  ||  et.sources.containsKey (sourceContainer)))
                                        {
                                            InternalBackendData sourceBed = (InternalBackendData) sourceContainer.backendData;
                                            EventSource es = new EventSource ();
                                            es.target       = et;
                                            es.monitorIndex = sourceBed.countLocalObject++;
                                            if (sourceContainer != s) es.reference = av.reference;  // null means self-reference, a special case handled in Part
                                            sourceBed.namesLocalObject.add ("event_monitor_" + s.prefix ());  // TODO: Consolidate monitors that share the same trigger condition.
                                            sourceBed.eventSources.add (es);
                                            et.sources.put (sourceContainer, es);
                                        }
                                        return false;
                                    }
                                    return true;
                                }
                            }
                            ConditionVisitor cv = new ConditionVisitor ();
                            de.operands[0].visit (cv);

                            //   Special case for event with no references that vary
                            if (et.sources.isEmpty ())
                            {
                                // We can avoid creating a self monitor if we know for certain that the event will never fire
                                boolean neverFires = false;
                                if (de.operands[0] instanceof Constant)
                                {
                                    if (et.edge == EventTarget.NONZERO)
                                    {
                                        Type op0 = ((Constant) de.operands[0]).value;
                                        if (op0 instanceof Scalar)
                                        {
                                            neverFires = ((Scalar) op0).value == 0;
                                        }
                                        else
                                        {
                                            Backend.err.get ().println ("ERROR: Condition for event() must resolve to a number.");
                                            throw new Backend.AbortRun ();
                                        }
                                    }
                                    else if (et.edge != EventTarget.EVALUATE)
                                    {
                                        neverFires = true;
                                    }
                                }

                                if (! neverFires)
                                {
                                    EventSource es = new EventSource ();
                                    es.target       = et;
                                    es.monitorIndex = countLocalObject++;
                                    // es.reference should be null for self
                                    namesLocalObject.add ("event_monitor_" + s.prefix ());
                                    eventSources.add (es);
                                    et.sources.put (s, es);
                                }
                            }

                            // Determine if monitor needs to test every target, or if one representative target is sufficient
                            for (Entry<EquationSet,EventSource> e : et.sources.entrySet ())
                            {
                                EquationSet container = e.getKey ();
                                EventSource source    = e.getValue ();
                                // If all the variables used by the event expression are within the same source
                                // part, then the answer will be the same for all registered target parts. However,
                                // if any of the variables belong to a different source part, then it's possible for
                                // different combinations of instances (via references from the target) to be
                                // associated with any given source instance, so every target must be evaluated separately.
                                if (cv.containers.size () > 1) source.testEach = true;
                                if (dv.containers.size () > 1  ||  (dv.containers.size () == 1  &&  dv.containers.first () != container)) source.delayEach = true;
                            }

                            // Force multiple sources to generate only one event in a given cycle
                            if (et.sources.size () > 1  &&  et.edge == EventTarget.NONZERO)
                            {
                                et.timeIndex = countLocalFloat++;
                                namesLocalFloat.add ("event_time" + eventTargets.size ());
                            }
                        }
                    }
                }
                return true;
            }
        }

        EventVisitor eventVisitor = new EventVisitor ();
        for (Variable v : s.variables)
        {
            eventVisitor.found = false;
            v.visit (eventVisitor);
            if ((eventVisitor.found  ||  v.dependsOnEvent ())  &&  v.reference.variable != v) eventReferences.add (v);
        }
    }

    public void addReferenceGlobal (VariableReference r, EquationSet s)
    {
        if (globalReference.add (r))
        {
            r.index = countGlobalObject++;
            namesGlobalObject.add ("reference to " + r.variable.container.name);
        }
        else
        {
            r.index = globalReference.floor (r).index;
        }
    }

    public void addReferenceLocal (VariableReference r, EquationSet s)
    {
        // Avoid redundancy between references and connections, since many references simply target connection endpoints.
        Object o = r.resolution.getLast ();  // There should always be at least one entry. This is enforced by the caller.
        if (o instanceof ConnectionBinding  &&  s.connectionBindings.contains (o))
        {
            r.index = endpoints + ((ConnectionBinding) o).index;
            return;
        }

        if (localReference.add (r))
        {
            r.index = countLocalObject++;
            namesLocalObject.add ("reference to " + r.variable.container.name);
        }
        else
        {
            r.index = localReference.floor (r).index;
        }
    }

    public void analyze (EquationSet s)
    {
        System.out.println (s.name);
        if (s.connectionBindings != null)
        {
            endpoints = countLocalObject;  // Note that populations have already been allocated in the constructor.
            countLocalObject += s.connectionBindings.size ();
        }
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
                            if (av.reference.resolution.size () > 0) addReferenceGlobal (av.reference, s);
                            return false;
                        }
                        if (op instanceof Output)
                        {
                            Output o = (Output) op;
                            if (o.operands.length < 3)
                            {
                                o.index = countGlobalObject++;
                                namesGlobalObject.add ("columnName" + o.index);
                            }
                            return true;  // Continue descent, because parameters of output() may contain variable references
                        }
                        return true;
                    }
                });
                if (! v.hasAny (new String[] {"constant", "accessor", "readOnly"})  ||  v.hasAll (new String[] {"constant", "reference"}))  // eliminate non-computed values, unless they refer to a variable outside the immediate equation set
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    boolean updates  = ! initOnly  &&  v.equations.size () > 0  &&  (v.derivative == null  ||  v.hasAttribute ("updates"));
                    if (updates) globalUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        addReferenceGlobal (v.reference, s);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        if (! temporary  ||  v.hasUsers ()) globalInit.add (v);
                        if (! temporary  &&  ! v.hasAttribute ("dummy"))
                        {
                            if (! v.hasAttribute ("preexistent")) globalMembers.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite")  ||  v.assignment != Variable.REPLACE)
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
                            if (av.reference.resolution.size () > 0) addReferenceLocal (av.reference, s);
                            return false;
                        }
                        if (op instanceof Output)
                        {
                            Output o = (Output) op;
                            if (o.operands.length < 3)
                            {
                                o.index = countLocalObject++;
                                namesLocalObject.add ("columnName" + o.index);
                            }
                            return true;  // Continue descent, because parameters of output() may contain variable references
                        }
                        return true;
                    }
                });
                if (! v.hasAny (new String[] {"constant", "accessor", "readOnly"})  ||  v.hasAll (new String[] {"constant", "reference"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    boolean updates  = ! initOnly  &&  v.equations.size () > 0  &&  (v.derivative == null  ||  v.hasAttribute ("updates"));
                    if (updates) localUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        addReferenceLocal (v.reference, s);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        if (! temporary  ||  v.hasUsers ())
                        {
                            if (v.name.startsWith ("$")  ||  (temporary  &&  v.neededBySpecial ()))
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
                            if (v.hasAttribute ("externalWrite")  ||  v.assignment != Variable.REPLACE)
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

        populationCanGrowOrDie =  s.lethalP  ||  s.lethalType  ||  s.canGrow ();
        if (n != null)
        {
            populationCanResize = globalMembers.contains (n);

            // TODO: correctly detect whether $n is constant before running analyze()
            // The problem is that we need information about lethality to know if $n is constant.
            // However, we need to process constants to detect lethality. This is a circular dependency.
            // The solution may be to:
            //   assume $n is not constant
            //   determine lethality as early as possible
            //   rerun constant elimination (EquationSet.findConstants()) as soon as we are certain about $n
            // See EquationSet.forceTemporaryStorageForSpecials() for a related issue.
            if (! populationCanResize  &&  populationCanGrowOrDie  &&  n.hasUsers ())
            {
                Backend.err.get ().println ("WARNING: $n can change (due to structural dynamics) but it was detected as a constant. Equations that depend on $n may give incorrect results.");
            }
        }

        if (index != null)
        {
            indexNext = countGlobalFloat++;
            namesGlobalFloat.add ("indexNext");
            indexAvailable = countGlobalObject++;
            namesGlobalObject.add ("indexAvailable");
        }

        if (s.connected  ||  populationCanResize)  // track instances
        {
            instances = countGlobalObject++;
            namesGlobalObject.add ("instances");

            if (s.connected)  // in addition, track newly created instances
            {
                firstborn = countGlobalFloat++;
                namesGlobalFloat.add ("firstborn");
                newborn = countLocalFloat++;
                namesLocalFloat.add ("newborn");
            }
        }

        if (p != null)
        {
            Pdependencies = new ArrayList<Variable> ();
            for (Variable t : s.ordered)
            {
                if (t.hasAttribute ("temporary")  &&  p.dependsOn (t) != null)
                {
                    Pdependencies.add (t);
                }
            }
        }

        populationIndex = 0;
        if (s.container != null  &&  s.container.parts != null)  // check for null specifically to guard against the Wrapper equation set (which is not fully constructed)
        {
            for (EquationSet p : s.container.parts)
            {
                if (p == s) break;
                populationIndex++;
            }
        }

        if (s.connectionBindings != null)  // connection-specific stuff
        {
            int size = s.connectionBindings.size ();
            // endpoints is allocated at the top of this function, because it is needed for reference handling in the variable analysis loop
            projectDependencies = new Object[size];
            projectReferences   = new Object[size];

            count   = new int     [size];
            k       = new Variable[size];
            max     = new Variable[size];
            min     = new Variable[size];
            project = new Variable[size];
            radius  = new Variable[size];

            for (int i = 0; i < s.connectionBindings.size (); i++)
            {
                ConnectionBinding c = s.connectionBindings.get (i);
                count  [i] = -1;
                k      [i] = s.find (new Variable (c.alias + ".$k"      ));
                max    [i] = s.find (new Variable (c.alias + ".$max"    ));
                min    [i] = s.find (new Variable (c.alias + ".$min"    ));
                project[i] = s.find (new Variable (c.alias + ".$project"));
                radius [i] = s.find (new Variable (c.alias + ".$radius" ));

                if (c.endpoint.accountableConnections != null)
                {
                    AccountableConnection query = new AccountableConnection (s, c.alias);
                    AccountableConnection ac = c.endpoint.accountableConnections.floor (query);
                    if (ac.equals (query))  // Only true if this endpoint is accountable.
                    {
                        // Allocate space for counter in target part
                        InternalBackendData endpointBed = (InternalBackendData) c.endpoint.backendData;
                        count[i] = endpointBed.countLocalFloat++;
                        endpointBed.namesLocalFloat.add (s.prefix () + ".$count");
                        if (ac.count != null)  // $count is referenced explicitly, so need to finish setting it up
                        {
                            ac.count.readIndex = ac.count.writeIndex = count[i];
                        }
                    }
                }

                namesLocalObject.add (c.alias);  // Note that countLocalObject has already been incremented above

                if (project[i] != null)
                {
                    ArrayList<Variable> dependencies = new ArrayList<Variable> ();
                    projectDependencies[i] = dependencies;  // Always assign, even if empty.
                    for (Variable t : s.ordered)
                    {
                        if (t.hasAttribute ("temporary")  &&  project[i].dependsOn (t) != null)
                        {
                            dependencies.add (t);
                        }
                    }

                    final TreeSet<VariableReference> references = new TreeSet<VariableReference> (referenceComparator);
                    class ProjectVisitor extends Visitor
                    {
                        public boolean visit (Operator op)
                        {
                            if (op instanceof AccessVariable)
                            {
                                AccessVariable av = (AccessVariable) op;
                                if (av.reference.resolution.size () > 0) references.add (av.reference);
                                return false;
                            }
                            return true;
                        }
                    }
                    ProjectVisitor visitor = new ProjectVisitor ();
                    project[i].visit (visitor);
                    for (Variable v : dependencies) v.visit (visitor);
                    if (references.size () > 0) projectReferences[i] = references;
                }

                c.resolution = translateResolution (c.resolution, s);
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

        if      (live.hasAttribute ("constant")) liveStorage = LIVE_CONSTANT;
        else if (live.hasAttribute ("accessor")) liveStorage = LIVE_ACCESSOR;
        else                                     liveStorage = LIVE_STORED;  // $live is "initOnly"

        for (VariableReference r : localReference ) r.resolution = translateResolution (r.resolution, s);
        for (VariableReference r : globalReference) r.resolution = translateResolution (r.resolution, s);

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
                for (ConnectionBinding c : s.connectionBindings)
                {
                    conversion.bindings[i] = -1;
                    int j = 0;
                    for (ConnectionBinding d : target.connectionBindings)
                    {
                        if (c.alias.equals (d.alias))
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
        boolean dtCanChange =  dt != null  &&  dt.equations.size () > 0  &&  ! dt.hasAttribute ("initOnly");
        // Note: dt can also change if we use a variable-step integrator. At present, Internal doesn't do that,
        // and it is unlikely to ever do so.

        if (hasIntegrated  &&  (eventTargets.size () > 0  ||  dtCanChange))
        {
            lastT = new Variable ("$lastT");
            lastT.readIndex = lastT.writeIndex = countLocalFloat++;
            namesLocalFloat.add (lastT.nameString ());
            lastT.type = new Scalar (0);
        }
    }

    public static class ResolveContainer implements Instance.Resolver
    {
        public Instance resolve (Instance from)
        {
            if (from instanceof Population) return from.container;
            else                            return from.container.container;  // Parts must dereference their Population to get to their true container.
        }
    }

    public static class ResolvePart implements Instance.Resolver
    {
        public int i;

        public ResolvePart (int i)
        {
            this.i = i;
        }

        public Instance resolve (Instance from)
        {
            if (from instanceof Population)
            {
                Backend.err.get ().println ("ERROR: Attempt to resolve a population within another population with iterating over instances. Instead, create a nested connection.");
                throw new Backend.AbortRun ();
            }
            return (Instance) from.valuesObject[i];
        }
    }

    /**
         Convert resolution to a form that can be processed quickly at runtime.
    **/
    public LinkedList<Object> translateResolution (LinkedList<Object> resolution, EquationSet current)
    {
        LinkedList<Object> newResolution = new LinkedList<Object> ();
        Iterator<Object> it = resolution.iterator ();
        while (it.hasNext ())
        {
            Object o = it.next ();
            if (o instanceof EquationSet)  // We are following the containment hierarchy.
            {
                EquationSet next = (EquationSet) o;
                if (next == current.container)  // ascend to our container
                {
                    newResolution.add (new ResolveContainer ());
                }
                else  // descend to one of our contained populations
                {
                    int i = 0;
                    for (EquationSet p : current.parts)
                    {
                        if (p == next)
                        {
                            newResolution.add (new ResolvePart (i));
                            break;
                        }
                        i++;
                    }
                    if (i > current.parts.size ()) throw new EvaluationException ("Could not find connection target.");
                }
                current = next;
            }
            else if (o instanceof ConnectionBinding)  // We are following a part reference, which means "current" is a connection.
            {
                ConnectionBinding c = (ConnectionBinding) o;
                InternalBackendData bed = (InternalBackendData) current.backendData;
                newResolution.add (new ResolvePart (bed.endpoints + c.index));
                current = c.endpoint;
            }
        }
        return newResolution;
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

        System.out.println ("  eventReferences:");
        for (Variable v : eventReferences) System.out.println ("    " + v.nameString () + " in " + v.container.name);
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
