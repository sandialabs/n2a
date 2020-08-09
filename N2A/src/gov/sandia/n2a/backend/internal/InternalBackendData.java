/*
Copyright 2015-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.AccountableConnection;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.EquationSet.ReplaceConstants;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Delay;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class InternalBackendData
{
    public Object backendData;  ///< Other backends may use Internal as a preprocessor, and may need to store additional data not covered here.

    public List<Variable> localUpdate                  = new ArrayList<Variable> ();  // updated during regular call to update()
    public List<Variable> localInit                    = new ArrayList<Variable> ();  // variables set by init()
    public List<Variable> localMembers                 = new ArrayList<Variable> ();  // stored inside the object
    public List<Variable> localBufferedInternal        = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
    public List<Variable> localBufferedInternalUpdate  = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
    public List<Variable> localBufferedExternal        = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
    public List<Variable> localBufferedExternalWrite   = new ArrayList<Variable> ();  // subset of external that are due to external write
    public List<Variable> localIntegrated              = new ArrayList<Variable> ();  // store the result of integration of some other variable (the derivative)
    public List<Variable> globalUpdate                 = new ArrayList<Variable> ();
    public List<Variable> globalInit                   = new ArrayList<Variable> ();
    public List<Variable> globalMembers                = new ArrayList<Variable> ();
    public List<Variable> globalBufferedInternal       = new ArrayList<Variable> ();
    public List<Variable> globalBufferedInternalUpdate = new ArrayList<Variable> ();
    public List<Variable> globalBufferedExternal       = new ArrayList<Variable> ();
    public List<Variable> globalBufferedExternalWrite  = new ArrayList<Variable> ();
    public List<Variable> globalIntegrated             = new ArrayList<Variable> ();

    public TreeSet<VariableReference> localReference   = new TreeSet<VariableReference> ();
    public TreeSet<VariableReference> globalReference  = new TreeSet<VariableReference> ();

    // The following arrays have exactly the same order as EquationSet.connectionBindings
    public int      endpoints;           // Position in valuesObject of first reference to a connected instance. References are allocated as a contiguous block.
    public int[]    count;               // Position in endpoint.valuesFloat of count value for this connection
    public Object[] projectDependencies; // References used within a $project expression
    public Object[] projectReferences;   // References used within a $project expression

    // Ready-to-use handles for common $variables
    // Arrays are associated with connectionBindings, as above. Elements may be null if not applicable.
    public Variable   connect;
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

    public List<Variable> Pdependencies;   // Contains any temporary variables (in evaluation order) that $p depends on. Guaranteed non-null if $p is non-null.
    public List<Variable> XYZdependencies; // Contains any temporary variables (in evaluation order) that $xyz depends on. Guaranteed non-null if $xyz is non-null.

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
    public List<Delay>       delays          = new ArrayList<Delay> ();     // Not related to events, but processed in a similar manner.

    public boolean singleton;               // $n=1 always; No structural dynamics.
    public boolean populationCanGrowOrDie;  // by structural dynamics other than $n
    public boolean populationCanResize;     // by manipulating $n
    public int     populationIndex;         // in container.populations

    public double  poll = -1;               // For connections, how much time is allowed to check full set of latent connections. Zero means every cycle. Negative means don't poll.
    public int     pollDeadline;            // position in population valuesFloat of time by which current poll cycle must complete. Only valid if poll>=0.
    public int     pollSorted;              // position in population valuesObject of sorted list of connections. Only valid if poll>=0.

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

    public static class EventTarget
    {
        public EquationSet       container;
        public Event             event;             // For evaluating whether the event should be triggered. There may be several equivalent event() calls in the part, so this is just one representative of the group.
        public int               valueIndex;        // position of bit array in valuesFloat
        public int               mask;              // an unsigned AND between this and the (int cast) entry from valuesFloat will indicate event active
        public int               edge         = RISE;
        public double            delay        = -1; // default is no-care; Indicates to process event in next regularly scheduled cycle of the target part
        public int               timeIndex    = -1; // position in valuesFloat of timestamp when last event to this target was generated; used to force multiple sources to generate only one event in a given cycle; -1 means the guard is unneeded
        public List<EventSource> sources      = new ArrayList<EventSource> ();
        public List<Variable>    dependencies = new ArrayList<Variable> ();

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
        public static final int RISE     = 0;
        public static final int FALL     = 1;
        public static final int CHANGE   = 2;
        public static final int NONZERO  = 3;

        public EventTarget (Event event)
        {
            this.event = event;
        }

        public boolean monitors (EquationSet sourceContainer)
        {
            for (EventSource es : sources) if (es.container == sourceContainer) return true;
            return false;
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
            InstanceTemporaries temp = new InstanceTemporaries (targetPart, simulator);
            for (Variable v : dependencies)
            {
                Type result = v.eval (temp);
                if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
            }

            double before = 0;
            if (edge != NONZERO) before = ((Scalar) temp.get (track.reference)).value;

            double after;
            if (trackOne)  // This is a single variable, so check its value directly.
            {
                after = ((Scalar) temp.getFinal (track.reference)).value;
            }
            else  // This is an expression, so use our private auxiliary variable.
            {
                Scalar result = (Scalar) event.operands[0].eval (temp);
                if (edge != NONZERO) temp.setFinal (track, result);  // Since the variable is effectively hidden, we don't wait for the finalize phase.
                after = result.value;
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
            InstanceTemporaries temp = new InstanceTemporaries (targetPart, simulator);
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
            return event.equals (((EventTarget) that).event);
        }
    }

    public static class EventSource
    {
        public EquationSet       container;
        public EventTarget       target;
        public int               monitorIndex; // position of monitor array in source_instance.valuesObject
        public VariableReference reference;    // for determining index of source part in target_instance.valuesObject. This is done indirectly so that event analysis can be done before indices are fully assigned.
        public boolean           testEach;     // indicates that the monitor must test each target instance, generally because the trigger references variables outside the source part
        public boolean           delayEach;    // indicates that the monitor evaluate the delay for each target instance

        public EventSource (EquationSet container, EventTarget target)
        {
            this.container = container;
            this.target    = target;
        }
    }

    public InternalBackendData (EquationSet s)
    {
        // Allocate space for populations before anything else has a chance.
        // All other code assumes populations are the first entries in valuesObject.
        for (EquationSet p : s.parts)
        {
            allocateLocalObject (p.name);
        }
    }

    /**
        Find event() calls and collate them (in case the same signature appears several different places
        in the equation set).
        This must be done before the variables are sorted into sets according to attributes, because we
        may need to add the "externalRead" attribute to some of them.
    **/
    public void analyzeEvents (EquationSet s)
    {
        analyzeEvents (s, eventTargets, eventReferences, delays);

        // Allocate storage for Event
        int valueIndex = -1;
        int mask       = 0;
        int eventIndex = 0;
        for (EventTarget et : eventTargets)
        {
            if (valueIndex == -1)
            {
                valueIndex = countLocalFloat++;
                namesLocalFloat.add ("eventLatch" + valueIndex);
                eventLatches.add (valueIndex);
                mask = 1;
            }
            et.valueIndex = valueIndex;
            et.mask       = mask;
            mask <<= 1;
            if (mask > 0x400000) valueIndex = -1;  // Due to limitations of float-int conversion, only 23 bits are available. Allocate another float.

            if (! et.trackOne  &&  et.edge != EventTarget.NONZERO)  // We have an auxiliary variable.
            {
                et.track.readIndex = et.track.writeIndex = allocateLocalFloat (et.track.name);
                // Preemptively add this, because the main analyze() routine won't see it.
                // We put the aux in init so that it can pick up the initial value without a call
                // to EventTarget.test(). Note that dependencies have been set when the aux was
                // created, so it will execute in the correct order with all the other init variables.
                localInit.add (et.track);
            }

            // Force multiple sources to generate only one event in a given cycle
            if (et.sources.size () > 1  &&  et.edge == EventTarget.NONZERO)
            {
                et.timeIndex = allocateLocalFloat ("eventTime" + eventIndex);
            }

            // TODO: What if two different event targets in this part reference the same source part? What if the condition is different? The same?
            for (EventSource es : et.sources)
            {
                EquationSet sourceContainer = es.container;
                InternalBackendData sourceBed = (InternalBackendData) sourceContainer.backendData;
                es.monitorIndex = sourceBed.allocateLocalObject ("eventMonitor_" + s.prefix ());  // TODO: Consolidate monitors that share the same trigger condition.
                sourceBed.eventSources.add (es);
            }

            eventIndex++;
        }

        // Allocate storage for Delay
        int i = 0;
        for (Delay d : delays)
        {
            d.index = allocateLocalObject ("delay" + i++);
        }
    }

    public static void analyzeEvents (EquationSet s, List<EventTarget> eventTargets, List<Variable> eventReferences, List<Delay> delays)
    {
        class EventVisitor implements Visitor
        {
            public boolean found;

            public boolean visit (Operator op)
            {
                if (op instanceof Delay)
                {
                    delays.add ((Delay) op);
                }
                else if (op instanceof Event)
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
                            targetIndex = eventTargets.size ();
                            eventTargets.add (et);
                            de.eventType = et;
                            et.container = s;

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
                                    Backend.err.get ().println ("ERROR: event() edge type must be a string.");
                                    throw new Backend.AbortRun ();
                                }
                            }
                            else
                            {
                                Backend.err.get ().println ("ERROR: event() edge type must be constant.");
                                throw new Backend.AbortRun ();
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
                                    Backend.err.get ().println ("WARNING: Cannot be temporary due to event monitor: " + v.fullName () + " from " + s.name);
                                    v.removeAttribute ("temporary");
                                }

                                // Treat temporaries like expressions (ie: create an auxiliary variable to track changes in its value),
                                // so fall through to the !trackOne case below.
                                if (! v.hasAttribute ("temporary"))
                                {
                                    v.addAttribute ("externalRead");  // ensure it's buffered, so we can detect change
                                    et.trackOne = true;
                                    et.track = new Variable ("");  // just a holder for the reference
                                    et.track.reference = reference;
                                }
                            }
                            if (! et.trackOne  &&  et.edge != EventTarget.NONZERO)  // Expression, so create auxiliary variable. Aux not needed for NONZERO, because no change detection.
                            {
                                et.track = new Variable ("$eventAux" + targetIndex, 0);
                                et.track.container = s;
                                et.track.type = new Scalar (0);
                                et.track.reference = new VariableReference ();
                                et.track.reference.variable = et.track;

                                // Make executable so it can be directly evaluated during the init cycle.
                                et.track.equations = new TreeSet<EquationEntry> ();
                                EquationEntry ee = new EquationEntry (et.track, "");
                                et.track.equations.add (ee);
                                ee.expression = et.event.operands[0].deepCopy ();
                                ee.expression.addDependencies (et.track);
                            }

                            // Locate any temporaries for evaluation.
                            //   Tie into the dependency graph using a phantom variable (which can go away afterward without damaging the graph).
                            // TODO: for more efficiency, we could have separate lists of temporaries for the condition and delay operands
                            // TODO: for more efficiency, cut off search for temporaries along a given branch of the tree at the first non-temporary.
                            final Variable phantom = new Variable ("event");
                            phantom.uses = new IdentityHashMap<Variable,Integer> ();
                            phantom.container = s;
                            et.event.visit (new Visitor ()
                            {
                                public boolean visit (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        Variable v = av.reference.variable;
                                        if (v.hasAttribute ("temporary")  &&  ! phantom.uses.containsKey (v)) phantom.uses.put (v, 1);
                                        return false;
                                    }
                                    return true;
                                }
                            });
                            //   Scan all variables in equation set to see if we need them
                            for (Variable t : s.ordered)
                            {
                                if (t.hasAttribute ("temporary")  &&  phantom.dependsOn (t) != null) et.dependencies.add (t);
                            }

                            // Delay
                            // Note the default is already set to -1 (no care)
                            class DelayVisitor implements Visitor
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
                            class ConditionVisitor implements Visitor
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
                                        if (! v.hasAttribute ("constant")  &&  ! v.hasAttribute ("initOnly")  &&  ! et.monitors (sourceContainer))
                                        {
                                            EventSource es = new EventSource (sourceContainer, et);
                                            if (sourceContainer != s) es.reference = av.reference;  // null means self-reference, a special case handled in Part
                                            et.sources.add (es);
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
                                    else
                                    {
                                        neverFires = true;
                                    }
                                }

                                if (! neverFires)
                                {
                                    EventSource es = new EventSource (s, et);
                                    // This is a self-reference, so es.reference should be null.
                                    et.sources.add (es);
                                }
                            }

                            // Determine if monitor needs to test every target, or if one representative target is sufficient
                            for (EventSource source : et.sources)
                            {
                                // If all the variables used by the event expression are within the same source
                                // part, then the answer will be the same for all registered target parts. However,
                                // if any of the variables belong to a different source part, then it's possible for
                                // different combinations of instances (via references from the target) to be
                                // associated with any given source instance, so every target must be evaluated separately.
                                if (cv.containers.size () > 1) source.testEach = true;
                                if (dv.containers.size () > 1  ||  (dv.containers.size () == 1  &&  dv.containers.first () != source.container)) source.delayEach = true;
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
            r.index = allocateGlobalObject ("reference to " + r.variable.container.name);
        }
        else
        {
            r.index = globalReference.floor (r).index;
        }
    }

    public void addReferenceLocal (VariableReference r, EquationSet s)
    {
        // Avoid redundancy between references and connections, since many references simply target connection endpoints.
        Object last = r.resolution.get (r.resolution.size () - 1);  // There should always be at least one entry. This is enforced by the caller.
        if (last instanceof ConnectionBinding  &&  s.connectionBindings.contains (last))
        {
            r.index = endpoints + ((ConnectionBinding) last).index;
            return;
        }

        // Don't do resolution if target is an immediate down-reference to a global variable (rare case).
        if (last instanceof EquationSet  &&  r.variable.hasAttribute ("global"))
        {
            r.index = s.parts.indexOf (last);  // This search may fail.
            if (r.index >= 0) return;  // This is an immediate down-reference, so done.
            // else fall through to usual case ...
        }

        // TODO: Avoid redundant reference to our container.
        // Right now, if any variable references our container, an entry is made in valuesObject.
        // That entry is redundant with Instance.container. However, the options for avoiding it both
        // involve ugly code:
        // 1) Get rid of of Instance.container and always store container in (say) valuesObject[0].
        //    Must unpack container everywhere it is used.
        // 2) Use VariableReference.index==-1 to indicate container (and -2 to indicate no reference). 
        //    Must add extra case everywhere that index is used.

        if (localReference.add (r))
        {
            r.index = allocateLocalObject ("reference to " + r.variable.container.name);
        }
        else
        {
            r.index = localReference.floor (r).index;
        }
    }

    public void analyze (final EquationSet s)
    {
        System.out.println (s.name);
        if (s.connectionBindings != null)
        {
            endpoints = countLocalObject;  // Note that populations have already been allocated in the constructor.
            countLocalObject += s.connectionBindings.size ();
        }

        List<String> forbiddenLocalInit = Arrays.asList ("$index", "$live", "$type");
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            String className = "null";
            if (v.type != null) className = v.type.getClass ().getSimpleName ();
            String dimensionName = "";
            if (v.unit != null) dimensionName = v.unit.toString ();
            System.out.println ("  " + v.nameString () + " " + v.attributeString () + " " + className + " " + dimensionName);

            if      (v.name.equals ("$connect")                  ) connect = v;
            else if (v.name.equals ("$index"  )                  ) index   = v;
            else if (v.name.equals ("$init"   )                  ) init    = v;
            else if (v.name.equals ("$live"   )                  ) live    = v;
            else if (v.name.equals ("$n"      )  &&  v.order == 0) n       = v;
            else if (v.name.equals ("$p"      )  &&  v.order == 0) p       = v;
            else if (v.name.equals ("$type"   )                  ) type    = v;
            else if (v.name.equals ("$xyz"    )  &&  v.order == 0) xyz     = v;
            else if (v.name.equals ("$t"      ))
            {
                if      (v.order == 0) t  = v;
                else if (v.order == 1) dt = v;
            }

            boolean initOnly        = v.hasAttribute ("initOnly");
            boolean emptyCombiner   = v.isEmptyCombiner ();
            boolean updates         = ! initOnly  &&  v.equations.size () > 0  &&  ! emptyCombiner  &&  (v.derivative == null  ||  v.hasAttribute ("updates"));
            boolean temporary       = v.hasAttribute ("temporary");
            boolean unusedTemporary = temporary  &&  ! v.hasUsers ();
            if (v.hasAttribute ("externalWrite")) v.externalWrite = true;

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
                            if (! o.hasColumnName)
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
                    if (updates) globalUpdate.add (v);
                    if (! unusedTemporary  &&  ! emptyCombiner) globalInit.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        addReferenceGlobal (v.reference, s);
                    }
                    else if (! temporary  &&  ! v.hasAttribute ("dummy"))
                    {
                        if (! v.hasAttribute ("preexistent")) globalMembers.add (v);

                        boolean external = false;
                        if (v.externalWrite  ||  v.assignment != Variable.REPLACE)
                        {
                            external = true;
                            globalBufferedExternalWrite.add (v);
                        }
                        if (external  ||  (v.hasAttribute ("externalRead")  &&  updates))
                        {
                            external = true;
                            globalBufferedExternal.add (v);
                        }
                        if (! external  &&  v.hasAttribute ("cycle"))
                        {
                            globalBufferedInternal.add (v);
                            if (! initOnly) globalBufferedInternalUpdate.add (v);
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
                            if (! o.hasColumnName)
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
                    if (updates) localUpdate.add (v);
                    if (! unusedTemporary  &&  ! emptyCombiner  &&  ! forbiddenLocalInit.contains (v.name)) localInit.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        addReferenceLocal (v.reference, s);
                    }
                    else if (! temporary  &&  ! v.hasAttribute ("dummy"))
                    {
                        if (! v.hasAttribute ("preexistent")) localMembers.add (v);

                        boolean external = false;
                        if (v.externalWrite  ||  v.assignment != Variable.REPLACE)
                        {
                            external = true;
                            localBufferedExternalWrite.add (v);
                        }
                        if (external  ||  (v.hasAttribute ("externalRead")  &&  updates))
                        {
                            external = true;
                            localBufferedExternal.add (v);
                        }
                        if (! external  &&  v.hasAttribute ("cycle"))
                        {
                            localBufferedInternal.add (v);
                            if (! initOnly) localBufferedInternalUpdate.add (v);
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

        determineOrderInit (s, localInit);
        determineOrderInit (s, globalInit);

        singleton = s.isSingleton (true);
        populationCanGrowOrDie =  s.lethalP  ||  s.lethalType  ||  s.canGrow ();
        if (n != null  &&  ! singleton)
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

        if (index != null  &&  ! singleton)
        {
            indexNext = allocateGlobalFloat ("indexNext");
            indexAvailable = allocateGlobalObject ("indexAvailable");
        }

        if (singleton  ||  s.connected  ||  s.needInstanceTracking  ||  populationCanResize)  // track instances
        {
            // The reason populationCanResize forces use of the instances array is to enable pruning of parts when $n decreases.
            // The reason to "track instances" for a singleton is to allocate a slot for direct storage of the single instance in valuesObject.

            instances = allocateGlobalObject ("instances");

            if (s.connected)  // in addition, track newly created instances
            {
                if (! singleton)
                {
                    firstborn = allocateGlobalFloat ("firstborn");
                }
                newborn = allocateLocalFloat ("newborn");
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

            poll = s.determinePoll ();
            if (poll >= 0)
            {
                pollDeadline = allocateGlobalFloat ("pollDeadline");
                pollSorted   = allocateGlobalObject ("pollSorted");
            }
        }

        if (type != null)
        {
            for (EquationEntry e : type.equations)
            {
                Split split = (Split) e.expression;
                split.index = type.reference.variable.container.splits.indexOf (split.parts);
            }
        }

        if (xyz != null)
        {
            XYZdependencies = new ArrayList<Variable> ();
            for (Variable t : s.ordered)
            {
                if (t.hasAttribute ("temporary")  &&  xyz.dependsOn (t) != null)
                {
                    XYZdependencies.add (t);
                }
            }
        }

        populationIndex = 0;
        if (s.container != null  &&  s.container.parts != null)  // check for null specifically to guard against the Wrapper equation set (which is not fully constructed)
        {
            populationIndex = s.container.parts.indexOf (s);
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
                        count[i] = endpointBed.allocateLocalFloat (s.prefix () + ".$count");
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

                    final TreeSet<VariableReference> references = new TreeSet<VariableReference> ();
                    class ProjectVisitor implements Visitor
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
            // in the object array rather than the float array.
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.readIndex = v.writeIndex = allocateLocalFloat (v.nameString ());
            }
            else
            {
                v.readIndex = v.writeIndex = allocateLocalObject (v.nameString ());
            }
        }
        for (Variable v : localBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = allocateLocalFloat ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = allocateLocalObject ("next_" + v.nameString ());
            }
        }
        for (Variable v : localBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = allocateLocalTempFloat ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = allocateLocalTempObject ("next_" + v.nameString ());
            }
        }

        //   Globals
        for (Variable v : globalMembers)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.readIndex = v.writeIndex = allocateGlobalFloat (v.nameString ());
            }
            else
            {
                v.readIndex = v.writeIndex = allocateGlobalObject (v.nameString ());
            }
        }
        for (Variable v : globalBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = allocateGlobalFloat ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = allocateGlobalObject ("next_" + v.nameString ());
            }
        }
        for (Variable v : globalBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v)
            {
                v.writeIndex = allocateGlobalTempFloat ("next_" + v.nameString ());
            }
            else
            {
                v.writeIndex = allocateGlobalTempObject ("next_" + v.nameString ());
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
                    v.readIndex = v.writeIndex = allocateGlobalTempFloat (v.nameString ());
                }
                else
                {
                    v.readIndex = v.writeIndex = allocateGlobalTempObject (v.nameString ());
                }
            }
            else
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v)
                {
                    v.readIndex = v.writeIndex = allocateLocalTempFloat (v.nameString ());
                }
                else
                {
                    v.readIndex = v.writeIndex = allocateLocalTempObject (v.nameString ());
                }
            }
        }

        if      (live.hasAttribute ("constant")) liveStorage = LIVE_CONSTANT;
        else if (live.hasAttribute ("accessor")) liveStorage = LIVE_ACCESSOR;
        else                                     liveStorage = LIVE_STORED;  // $live is "initOnly"

        for (VariableReference r : localReference ) r.resolution = translateResolution (r.resolution, s);
        for (VariableReference r : globalReference) r.resolution = translateResolution (r.resolution, s);
    }

    public void analyzeConversions (EquationSet s)
    {
        // Type conversions
        List<String> forbiddenVariables = Arrays.asList ("$type", "$live", "$index");  // forbid $index and all phase variables. TODO: create attributes just for this?
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
            InternalBackendData targetBed = (InternalBackendData) target.backendData;
            for (Variable v : targetBed.localMembers)
            {
                if (forbiddenVariables.contains (v.name)) continue;
                Variable v2 = s.find (v);
                if (v2 != null  &&  v2.compareTo (v) == 0)  // compareTo() does not consider container
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
            lastT.readIndex = lastT.writeIndex = allocateLocalFloat (lastT.nameString ());
            lastT.type = new Scalar (0);
        }
    }

    public int allocateGlobalFloat (String name)
    {
        namesGlobalFloat.add (name);
        return countGlobalFloat++;
    }

    public int allocateGlobalObject (String name)
    {
        namesGlobalObject.add (name);
        return countGlobalObject++;
    }

    public int allocateLocalFloat (String name)
    {
        namesLocalFloat.add (name);
        return countLocalFloat++;
    }

    public int allocateLocalObject (String name)
    {
        namesLocalObject.add (name);
        return countLocalObject++;
    }

    public int allocateGlobalTempFloat (String name)
    {
        namesGlobalTempFloat.add (name);
        return countGlobalFloat++;
    }

    public int allocateGlobalTempObject (String name)
    {
        namesGlobalTempObject.add (name);
        return countGlobalTempObject++;
    }

    public int allocateLocalTempFloat (String name)
    {
        namesLocalTempFloat.add (name);
        return countLocalTempFloat++;
    }

    public int allocateLocalTempObject (String name)
    {
        namesLocalTempObject.add (name);
        return countLocalTempObject++;
    }

    /**
        Rebuild dependencies based only on equations that can actually fire.
        Note that the C backend uses EquationSet.simplify() to do the same task.
        That function relies on Variable.deepCopy(List<Variable>) to duplicate the
        list and establish an internally-coherent set of dependencies.
        We don't do that here because we need to keep Java object identity across all lists.
    **/
    public static void determineOrderInit (EquationSet s, List<Variable> list)
    {
        for (Variable v : list)
        {
            v.usedBy = null;
            v.uses = null;
        }

        ReplaceConstants replace = s.new ReplaceConstants ("$init");

        class DependencyTransformer implements Transformer
        {
            public Variable v;
            public Operator transform (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable listVariable = EquationSet.find (av.reference.variable, list);
                    if (listVariable != null)
                    {
                        av.reference = new VariableReference ();
                        av.reference.variable = listVariable;
                        v.addDependencyOn (listVariable);
                        return av;
                    }
                }
                return null;
            }
        }
        DependencyTransformer depend = new DependencyTransformer ();

        for (Variable v : list)
        {
            // Work through equations, adding dependencies for any that are ambiguous,
            // and terminate at the first one that will always fire.
            replace.self = v;
            depend.v = v;
            for (EquationEntry e : v.equations)
            {
                boolean couldFire   = true;
                boolean alwaysFires = true;
                if (e.condition != null)
                {
                    Operator test = e.condition.deepCopy ().transform (replace).simplify (v, true);
                    if (test.isScalar ())
                    {
                        couldFire = alwaysFires = test.getDouble () != 0;
                    }
                    else
                    {
                        alwaysFires = false;
                    }
                }
                if (couldFire) v.transform (depend);
                if (alwaysFires) break;  // Don't check any more equations, because Internal will stop here.
            }
        }

        EquationSet.determineOrderInit (list);
    }

    public static class ResolveContainer implements Instance.Resolver
    {
        public Instance resolve (Instance from)
        {
            return from.container;
        }

        public boolean shouldEnumerate (Instance from)
        {
            return false;
        }

        public String toString ()
        {
            return "ResolveContainer";
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
            return (Instance) from.valuesObject[i];
        }

        public boolean shouldEnumerate (Instance from)
        {
            return from instanceof Population;
        }

        public String toString ()
        {
            return "ResolvePart " + i;
        }
    }

    /**
         Convert resolution to a form that can be processed quickly at runtime.
    **/
    public ArrayList<Object> translateResolution (ArrayList<Object> resolution, EquationSet current)
    {
        ArrayList<Object> newResolution = new ArrayList<Object> ();
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
                    int i = current.parts.indexOf (next);
                    if (i < 0) throw new EvaluationException ("Could not find resolution target " + next.name + " in " + current.name);
                    newResolution.add (new ResolvePart (i));
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
        dumpVariableList ("localInit                   ", localInit);
        dumpVariableList ("localMembers                ", localMembers);
        dumpVariableList ("localBufferedInternal       ", localBufferedInternal);
        dumpVariableList ("localBufferedInternalUpdate ", localBufferedInternalUpdate);
        dumpVariableList ("localBufferedExternal       ", localBufferedExternal);
        dumpVariableList ("localBufferedExternalWrite  ", localBufferedExternalWrite);
        dumpVariableList ("localIntegrated             ", localIntegrated);

        dumpVariableList ("globalUpdate                ", globalUpdate);
        dumpVariableList ("globalInit                  ", globalInit);
        dumpVariableList ("globalMembers               ", globalMembers);
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
        for (VariableReference r : set)
        {
            System.out.println ("    " + r.variable.nameString () + " in " + r.variable.container.name + " " + r.resolution.size ());
            //for (Object o : r.resolution) System.out.println ("      " + o);
        }
    }
}
