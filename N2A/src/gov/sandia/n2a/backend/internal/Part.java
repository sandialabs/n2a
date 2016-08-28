/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.Map.Entry;

import gov.sandia.n2a.backend.internal.InternalBackendData.Conversion;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventSource;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

/**
    An Instance that is capable of holding sub-populations.
    Generally, this is any kind of Instance except a Population.
**/
public class Part extends Instance
{
    public Population[] populations;

    /// An empty constructor, specifically for use by Wrapper. If you're not Wrapper, don't use this!
    protected Part ()
    {
    }

    protected Part (EquationSet equations, Population container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countLocalFloat, bed.countLocalObject);
        if (equations.parts.size () > 0)
        {
            populations = new Population[equations.parts.size ()];
            int i = 0;
            for (EquationSet s : equations.parts)
            {
                if (s.connectionBindings == null) populations[i++] = new PopulationCompartment (s, this);
                else                              populations[i++] = new PopulationConnection  (s, this);
            }
        }
        for (EventSource es : bed.eventSources)
        {
            valuesObject[es.monitorIndex] = new ArrayList<Instance> ();
        }
    }

    public Type get (Variable v)
    {
        if (v.global) return container.get (v);  // forward global variables to our population object
        return super.get (v);
    }

    public void die ()
    {
        // set $live to false, if it is stored in this part
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            set (bed.live, new Scalar (0));
        }
    }

    public void resolve ()
    {
        resolve (((InternalBackendData) equations.backendData).localReference);
    }

    /**
        Note: specifically for Parts, call resolve() before calling init(). This is to
        accommodate the connection process, which must probe values in a part (which
        may include references) before calling init().
    **/
    public void init (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);

        // $variables
        if (temp.bed.liveStorage == InternalBackendData.LIVE_STORED) set (temp.bed.live, new Scalar (1));  // force $live to be set before anything else
        if (temp.bed.storeDt) temp.set (temp.bed.dt, new Scalar (((EventStep) simulator.currentEvent).dt));  // For $t' we could do a direct set, without going through temp.
        for (Variable v : temp.bed.localInitSpecial)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);

            // Note that some valuesObject entries may be left null. This is OK, because Instance.get() will return
            // a zero-equivalent value if it finds null. Ditto for non-$variables below.
        }
        for (Variable v : temp.bed.localBufferedSpecial)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        // non-$variables
        for (Variable v : temp.bed.localInitRegular)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        for (Variable v : temp.bed.localBufferedRegular)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        // zero external buffered variables that may be written before first finish()
        for (Variable v : temp.bed.localBufferedExternalWrite) set (v, v.type);  // v.type should be pre-loaded with zero-equivalent values

        // Note: instance counting is handled directly by PopulationCompartment.add()

        // Request event monitors
        for (EventTarget et : temp.bed.eventTargets)
        {
            for (Entry<EquationSet,EventSource> i : et.sources.entrySet ())
            {
                EventSource es = i.getValue ();
                Instance source = ((Instance) valuesObject[es.reference.index]);
                @SuppressWarnings("unchecked")
                ArrayList<Instance> monitors = (ArrayList<Instance>) source.valuesObject[es.monitorIndex];
                monitors.add (this);
            }
        }

        if (populations != null) for (Population p : populations) p.init (simulator);
    }

    public void integrate (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.localIntegrated.isEmpty ()  &&  populations == null) return;  // nothing to do

        double dt;
        if (bed.lastT == null) dt = ((EventStep) simulator.currentEvent).dt;
        else                   dt = simulator.currentEvent.t - ((Scalar) get (bed.lastT)).value;
        if (dt <= 0) return;  // nothing to do

        // Integrate variables
        for (Variable v : bed.localIntegrated)
        {
            double a  = ((Scalar) get (v           )).value;
            double aa = ((Scalar) get (v.derivative)).value;
            setFinal (v, new Scalar (a + aa * dt));
        }

        if (populations != null) for (Population p : populations) p.integrate (simulator, dt);
    }

    public void update (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
        for (Variable v : temp.bed.localUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                // Note: $type is explicitly evaluated to 0 in Variable.eval(), so it never returns null, even when no conditions match.

                // If variable is buffered, then we must copy its value to ensure it gets copied back
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.readIndex != v.writeIndex) temp.set (v, temp.get (v));
            }
            else if (v.reference.variable.writeIndex >= 0)  // ensure this is not a "dummy" variable
            {
                if (v.assignment == Variable.REPLACE)
                {
                    temp.set (v, result);
                }
                else
                {
                    // the rest of these require knowing the current value of the working result, which is most likely external buffered
                    Type current = temp.getFinal (v.reference);
                    if      (v.assignment == Variable.ADD)
                    {
                        temp.set (v, current.add (result));
                    }
                    else if (v.assignment == Variable.MULTIPLY)
                    {
                        temp.set (v, current.multiply (result));
                    }
                    else if (v.assignment == Variable.MAX)
                    {
                        if (((Scalar) result.GT (current)).value != 0) temp.set (v, result);
                    }
                    else if (v.assignment == Variable.MIN)
                    {
                        if (((Scalar) result.LT (current)).value != 0) temp.set (v, result);
                    }
                }
            }
        }
        for (Variable v : temp.bed.localBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        if (populations != null) for (Population p : populations) p.update (simulator);
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        // Events
        for (EventSource es : bed.eventSources)
        {
            @SuppressWarnings("unchecked")
            ArrayList<Instance> monitors = (ArrayList<Instance>) valuesObject[es.monitorIndex];
            EventTarget eventType = es.target;
            if (eventType.testAll)
            {
                for (Instance i : monitors)
                {
                    double delay = eventType.test (i, simulator);
                    if (delay == -2) continue;  // the trigger condition was not satisfied 
                    EventSpikeSingle spike;
                    if (delay >= 0)
                    {
                        spike = new EventSpikeSingle (); // : new EventSpikeSingleLatch ();
                        spike.t = simulator.currentEvent.t + delay;
                    }
                    else  // delay == -1 --> event was triggered, but timing is no-care
                    {
                        spike = new EventSpikeSingleLatch ();
                        spike.t = simulator.currentEvent.t;  // queue immediately after current cycle, so latches get set for next full cycle
                    }
                    spike.eventType = eventType;
                    simulator.eventQueue.add (spike);
                }
            }
            else if (monitors.size () > 0)
            {
                double delay = eventType.test (monitors.get (0), simulator);
                if (delay >= -1)  // the trigger condition was satisfied
                {
                    EventSpikeMulti spike;
                    if (delay >= 0)
                    {
                        spike = new EventSpikeMulti ();
                        spike.t = simulator.currentEvent.t + delay;
                    }
                    else
                    {
                        spike = new EventSpikeMultiLatch ();
                        spike.t = simulator.currentEvent.t;
                    }
                    spike.eventType = eventType;
                    // We don't copy the array, just keep a reference to it. What could go wrong with this?
                    // If a part dies and tries to remove itself from the list while it is being used to deliver spikes,
                    // then we could get a null pointer exception. Solution is to synchronize access to the list.
                    // If a connection is born while the spike is in flight, one could argue that it shouldn't
                    // receive it, but one could also argue that it should. In nature these two things (spikes
                    // and synapse creation) occur at vastly different timescales. Wouldn't a nascent synapse
                    // receive spikes even as it is forming?
                    spike.targets = monitors;
                    simulator.eventQueue.add (spike);
                }
            }
        }

        // Other stuff
        if (populations != null) for (Population p : populations) p.finish (simulator);

        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            if (((Scalar) get (bed.live)).value == 0) return false;  // early-out if we are already dead, to avoid another call to die()
        }

        if (bed.lastT != null) setFinal (bed.lastT, new Scalar (simulator.currentEvent.t));
        for (Variable v : bed.localBufferedExternal) setFinal (v, getFinal (v));
        for (Integer i : bed.eventLatches) valuesFloat[i] = 0;
        for (Variable v : bed.localBufferedExternalWrite)
        {
            switch (v.assignment)
            {
                case Variable.ADD:
                    set (v, v.type);  // initial value is zero-equivalent (additive identity)
                    break;
                // TODO: make the following cases type-sensitive
                case Variable.MULTIPLY:
                    set (v, new Scalar (1));  // multiplicative identity
                    break;
                case Variable.MIN:
                    set (v, new Scalar (Double.POSITIVE_INFINITY));
                    break;
                case Variable.MAX:
                    set (v, new Scalar (Double.NEGATIVE_INFINITY));
                    break;
                // For all other assignment types, do nothing. Effectively, buffered value is initialized to current value
            }
        }

        if (bed.type != null)
        {
            int type = (int) ((Scalar) get (bed.type)).value;
            if (type > 0)
            {
                ArrayList<EquationSet> split = equations.splits.get (type - 1);
                if (split.size () > 1  ||  split.get (0) != equations)  // Make sure $type != me. Otherwise it's a null operation
                {
                    boolean used = false;  // indicates that this instance is one of the resulting parts
                    int countParts = split.size ();
                    for (int i = 0; i < countParts; i++)
                    {
                        EquationSet other = split.get (i);
                        Scalar splitPosition = new Scalar (i+1);
                        if (other == equations  &&  ! used)
                        {
                            used = true;
                            setFinal (bed.type, splitPosition);
                        }
                        else
                        {
                            Part p = convert (other);

                            enqueue (p);
                            p.resolve ();
                            p.init (simulator);  // accountable connections are updated here

                            // Copy over variables
                            Conversion conversion = bed.conversions.get (other);
                            int count = conversion.from.size ();
                            for (int v = 0; v < count; v++)
                            {
                                Variable from = conversion.from.get (v);
                                Variable to   = conversion.to  .get (v);
                                p.setFinal (to, get (from));
                            }

                            // Set $type to be our position in the split
                            InternalBackendData otherBed = (InternalBackendData) other.backendData;
                            p.setFinal (otherBed.type, splitPosition);
                        }
                    }
                    if (! used)
                    {
                        die ();
                        return false;
                    }
                }
            }
        }

        if (equations.lethalP)
        {
            double p;
            if (bed.p.hasAttribute ("temporary"))
            {
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
                p = ((Scalar) bed.p.eval (temp)).value;
            }
            else
            {
                p = ((Scalar) get (bed.p)).value;
            }
            if (p == 0  ||  p < 1  &&  p < simulator.uniform.nextDouble ())
            {
                die ();
                return false;
            }
        }

        if (equations.lethalConnection)
        {
            int count = equations.connectionBindings.size ();
            for (int i = 0; i < count; i++)
            {
                if (! getPart (i).getLive ())
                {
                    die ();
                    return false;
                }
            }
        }

        if (equations.lethalContainer)
        {
            if (! ((Part) container.container).getLive ())
            {
                die ();
                return false;
            }
        }

        return true;
    }

    /**
        Hack to allow testing of lethConnection at this level of the class hierarchy.
    **/
    public Part getPart (int i)
    {
        throw new EvaluationException ("Internal error: only Connections can hold references to other Parts.");
    }

    public boolean getLive ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.liveStorage == InternalBackendData.LIVE_CONSTANT) return true;  // constant implies always live
        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            if (((Scalar) get (bed.live)).value == 0) return false;
        }

        if (equations.lethalConnection)
        {
            int count = equations.connectionBindings.size ();
            for (int i = 0; i < count; i++)
            {
                if (! getPart (i).getLive ())
                {
                    die ();
                    return false;
                }
            }
        }

        if (equations.lethalContainer)
        {
            if (! ((Part) container.container).getLive ())
            {
                die ();
                return false;
            }
        }

        return true;
    }

    public Matrix getXYZ (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.xyz != null)
        {
            if (bed.xyz.hasAny (new String[] {"constant", "temporary"}))
            {
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);  // getXYZ() calls occur only during the init cycle, specifically when testing a connection
                return (Matrix) bed.xyz.eval (temp);
            }
            return (Matrix) get (bed.xyz);
        }
        return new Matrix (3, 1);  // default is ~[0,0,0]
    }

    /**
        Create a new part based on a different equation set (or perhaps even the same one)
        and copy over all matching variables. Places result directly onto sim queue.
    **/
    public Part convert (EquationSet other)
    {
        throw new EvaluationException ("Internal error: convert() must be implemented by a specific subclass of Part");
    }
}
