/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.List;
import gov.sandia.n2a.backend.internal.InternalBackendData.Conversion;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventSource;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

/**
    An Instance that is capable of holding sub-populations.
    Generally, this is any kind of Instance except a Population.
**/
public class Part extends Instance
{
    public EventStep event;    // Every Part lives on some simulation queue, held by an EventStep object.
    public Part      next;     // simulation queue
    public Part      previous; // simulation queue

    /**
        Empty constructor, specifically for use by Wrapper and EventStep.
    **/
    public Part ()
    {
    }

    public Part (EquationSet equations, Part container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countLocalFloat, bed.countLocalObject);

        int i = 0;
        for (EquationSet s : equations.parts)
        {
            s.priority = i++;  // Should be exactly the same as s.backendData.populationIndex. This saves unpacking backendData of our children just to access them in valuesObject.
            valuesObject[s.priority] = new Population (s, this);
        }

        for (EventSource es : bed.eventSources)
        {
            valuesObject[es.monitorIndex] = new ArrayList<Instance> ();
        }
        for (EventTarget et : bed.eventTargets)
        {
            if (et.timeIndex >= 0) valuesFloat[et.timeIndex] = 10;  // Since value is modulo 1 second, this initial value is different than anything that might ever be set. This allows events to be generated when $t=0.
        }
    }

    public double getDt ()
    {
        return event.dt;
    }

    public Type get (Variable v)
    {
        if (v.global)  // forward global variables to our population object
        {
            InternalBackendData bed = (InternalBackendData) equations.backendData;
            return ((Population) container.valuesObject[bed.populationIndex]).get (v);
        }
        return super.get (v);
    }

    public void die ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.fastExit)
        {
            Simulator s = Simulator.instance.get ();
            s.stop     = true;
            s.fastExit = true;
            return;  // The bookkeeping below is no longer relevant.
        }

        // set $live to false, if it is stored in this part
        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            set (bed.live, new Scalar (0));
        }

        // update accountable endpoints
        if (bed.count != null)
        {
            int length = bed.count.length;
            for (int i = 0; i < length; i++)
            {
                if (bed.count[i] >= 0)
                {
                    Part p = (Part) valuesObject[bed.endpoints+i];
                    p.valuesFloat[bed.count[i]]--;
                }
            }
        }

        // Release event monitors
        for (EventTarget et : bed.eventTargets)
        {
            for (EventSource es : et.sources)
            {
                if (es.reference == null) continue;  // Don't bother with self-connection, since we are going away.
                Part source = (Part) valuesObject[es.reference.index];
                @SuppressWarnings("unchecked")
                ArrayList<Instance> monitors = (ArrayList<Instance>) source.valuesObject[es.monitorIndex];
                int index = monitors.indexOf (this);
                monitors.set (index, null);  // Actually removing the element can cause a concurrent modification exception. Instead, the monitors array will get flushed next time an event processes it.
            }
        }

        ((Population) container.valuesObject[bed.populationIndex]).remove (this);
    }

    public void resolve ()
    {
        resolve (((InternalBackendData) equations.backendData).localReference);
    }

    public void dequeue ()
    {
        if (event != null) event.dequeue (this);
    }

    /**
        Note: specifically for Parts, call resolve() separately before calling init().
        This is to accommodate the connection process, which must probe values in a part
        (which may include references) before calling init().
    **/
    public void init (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceInit (this, simulator);
        InternalBackendData bed = temp.bed;
        ((Population) container.valuesObject[bed.populationIndex]).insert (this);  // update $n and assign $index

        // update accountable endpoints
        // Note: these do not require resolve(). Instead, they access their target directly through the endpoints array.
        if (bed.count != null)
        {
            int length = bed.count.length;
            for (int i = 0; i < length; i++)
            {
                if (bed.count[i] >= 0)
                {
                    Part p = (Part) valuesObject[bed.endpoints+i];
                    p.valuesFloat[bed.count[i]]++;
                }
            }
        }

        // Initialize variables
        // Note that some valuesObject entries could be left null. This is OK, because Instance.get() will return
        // a zero-equivalent value if it finds null.
        clearExternalWriteInit (bed.localBufferedExternalWrite);  // So our intial values can be applied correctly.
        for (Variable v : bed.localInit)
        {
            Type result = v.eval (temp);
            if (result == null  ||  v.reference.variable.writeIndex < 0) continue;

            // For local fields that have external writers, the value set here will not be included
            // in the reduction for the next cycle, as if this value was finalized in the previous cycle.
            // This prevents double counting.
            // Likewise, if we write to another part, it will be treated as if it were combined
            // in the previous cycle, again preventing double counting in the other part.
            // This is analogous to zero-delay event processing.
            // What could go wrong? If parts are updating asynchronously, this will cause a
            // sudden jump in the working value of the other part which is not properly associated
            // with its finish() step. It would be as if the part had an extra cycle inserted.
            if (v.reference.variable == v)               temp.applyResultInit (v, result);
            else ((Instance) valuesObject[v.reference.index]).applyResultInit (v.reference.variable, result);  // TODO: Currently, Internal is single-threaded, but in a multithreaded version this will probably a lock.
        }
        if (bed.liveStorage == InternalBackendData.LIVE_STORED) set (bed.live, new Scalar (1));
        if (bed.lastT != null) temp.setFinal (bed.lastT, new Scalar (simulator.currentEvent.t));
        if (bed.type != null) temp.setFinal (bed.type, new Scalar (0));
        if (bed.setDt) simulator.move (this, ((Scalar) bed.dt.type).value);

        // Prepare variables that have a combiner, in case they get written before the first finish().
        clearExternalWriteBuffers (bed.localBufferedExternalWrite);  // skips REPLACE it because it is unnecessary when called from update(). Handle REPLACE separately ...
        for (Variable v : bed.localBufferedExternalWrite) if (v.assignment == Variable.REPLACE) temp.set (v, temp.get (v));  // This must come after the variables are initialized. Otherwise, there is no point.

        // Request event monitors
        for (EventTarget et : bed.eventTargets)
        {
            for (EventSource es : et.sources)
            {
                Part source;
                if (es.reference == null) source = this;
                else                      source = (Part) valuesObject[es.reference.index];
                @SuppressWarnings("unchecked")
                ArrayList<Instance> monitors = (ArrayList<Instance>) source.valuesObject[es.monitorIndex];
                monitors.add (this);
            }
        }

        if (equations.orderedParts != null)  // If there are parts at all, then orderedParts must be filled in correctly. Otherwise it may be null.
        {
            for (EquationSet s : equations.orderedParts) ((Population) valuesObject[s.priority]).init (simulator);
        }
    }

    public void integrate (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int populations = equations.parts.size ();
        if (bed.localIntegrated.isEmpty ()  &&  populations == 0) return;  // nothing to do

        double dt;
        if (bed.lastT == null) dt = ((EventStep) simulator.currentEvent).dt;
        else                   dt = simulator.currentEvent.t - ((Scalar) get (bed.lastT)).value;
        if (dt <= 0) return;  // nothing to do

        // Integrate variables
        for (Variable v : bed.localIntegrated)
        {
            if (v.type instanceof Scalar)
            {
                double a  = ((Scalar) get (v           )).value;
                double aa = ((Scalar) get (v.derivative)).value;
                setFinal (v, new Scalar (a + aa * dt));
            }
            else  // anything else (should be Matrix)
            {
                Type a  = get (v);
                Type aa = get (v.derivative);
                setFinal (v, a.add (aa.multiply (new Scalar (dt))));
            }
        }

        for (int i = 0; i < populations; i++)
        {
            Population p = (Population) valuesObject[i];
            if (p != null) p.integrate (simulator, dt);  // Null check is needed to skip over inactive populations.
        }
    }

    public void update (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator);
        for (Variable v : temp.bed.localUpdate)
        {
            Type result = v.eval (temp);
            if (v.reference.variable.writeIndex < 0) continue;  // this is a "dummy" variable, so calling eval() was all we needed to do
            if (result != null)
            {
                temp.applyResult (v, result);
            }
            else if (v.reference.variable == v  &&  v.equations.size () > 0)  // No condition fired, and we need to provide some default value.
            {
                if (v.readIndex == v.writeIndex)  // not buffered
                {
                    if (v.readTemp) temp.set (v, v.type);  // This is a pure temporary, so set value to default for use by later equations. Note that readTemp==writeTemp==true.
                }
                else  // buffered
                {
                    if (! v.externalWrite) temp.set (v, temp.get (v));  // Not an accumulator, so copy its value
                }
            }
        }
        for (Variable v : temp.bed.localBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        int populations = equations.parts.size ();
        for (int i = 0; i < populations; i++)
        {
            Population p = (Population) valuesObject[i];
            if (p != null) p.update (simulator);
        }
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        int populations = equations.parts.size ();
        for (int i = 0; i < populations; i++)
        {
            Population p = (Population) valuesObject[i];
            if (p != null) p.finish (simulator);
        }

        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            if (((Scalar) get (bed.live)).value == 0) return false;  // early-out if we are already dead, to avoid another call to die()
        }

        // Events
        // Notes on timing: The "delay" parameter in an event() statement is relative to when the condition
        // is evaluated, which means it is relative to where the monitor resides. This translates into
        // event source objects, which is what we are about to evaluate below. Thus, delays should be
        // relative to the current time of this part (the event source).
        // When this finish() function is called as part of processing an event with special
        // timing (zero delay or jittered ahead/behind the regular step time), it may generate additional
        // events, simply as a consequence of running. These secondary events are relative to the time
        // when they are evaluated, which is generally different than the triggering event.
        for (EventSource es : bed.eventSources)
        {
            @SuppressWarnings("unchecked")
            List<Instance> monitors = (ArrayList<Instance>) valuesObject[es.monitorIndex];
            if (monitors.size () == 0) continue;

            EventTarget eventType = es.target;
            if (es.testEach)
            {
                for (Instance i : monitors)
                {
                    if (i == null) continue;
                    double delay = eventType.test (i, simulator);
                    if (delay < -1) continue;  // the trigger condition was not satisfied

                    EventSpikeSingle spike;
                    if (delay < 0)  // event was triggered, but timing is no-care
                    {
                        spike = new EventSpikeSingleLatch ();
                        spike.t = simulator.currentEvent.t;  // queue immediately after current cycle, so latches get set for next full cycle
                    }
                    else if (delay == 0)  // process as close to current cycle as possible
                    {
                        spike = new EventSpikeSingle ();  // fully execute the event (not latch it)
                        spike.t = simulator.currentEvent.t;  // queue immediately
                    }
                    else
                    {
                        // Is delay a quantum number of $t' steps?
                        double ratio = delay / event.dt;
                        int    step  = (int) Math.round (ratio);
                        if (Math.abs (ratio - step) < 1e-3)
                        {
                            if (simulator.during) spike = new EventSpikeSingleLatch ();
                            else                  spike = new EventSpikeSingle ();
                            delay = step * event.dt;
                        }
                        else
                        {
                            spike = new EventSpikeSingle ();
                        }
                        spike.t = simulator.currentEvent.t + delay;
                    }
                    spike.eventType = eventType;
                    spike.target    = i;
                    simulator.queueEvent.add (spike);
                }
            }
            else  // All monitors share same condition, so only test one.
            {
                double delay = -2;
                for (Instance i : monitors)
                {
                    if (i == null) continue;
                    delay = eventType.test (i, simulator);
                    break;
                }
                if (delay < -1) continue;  // the trigger condition was not satisfied

                if (es.delayEach)  // Each target instance may require a different delay.
                {
                    for (Instance i : monitors)
                    {
                        if (i == null) continue;
                        delay = eventType.delay (i, simulator);  // This results in one redundant eval, of first entry in monitors. Not clear if it's worth the work to avoid this.

                        EventSpikeSingle spike;
                        if (delay < 0)
                        {
                            spike = new EventSpikeSingleLatch ();
                            spike.t = simulator.currentEvent.t;
                        }
                        else if (delay == 0)
                        {
                            spike = new EventSpikeSingle ();
                            spike.t = simulator.currentEvent.t;
                        }
                        else
                        {
                            double ratio = delay / event.dt;
                            int    step  = (int) Math.round (ratio);
                            if (Math.abs (ratio - step) < 1e-3)
                            {
                                if (simulator.during) spike = new EventSpikeSingleLatch ();
                                else                  spike = new EventSpikeSingle ();
                                delay = step * event.dt;
                            }
                            else
                            {
                                spike = new EventSpikeSingle ();
                            }
                            spike.t = simulator.currentEvent.t + delay;
                        }
                        spike.eventType = eventType;
                        spike.target    = i;
                        simulator.queueEvent.add (spike);
                    }
                }
                else  // All delays are the same.
                {
                    EventSpikeMulti spike;
                    if (delay < 0)
                    {
                        spike = new EventSpikeMultiLatch ();
                        spike.t = simulator.currentEvent.t;
                    }
                    else if (delay == 0)
                    {
                        spike = new EventSpikeMulti ();
                        spike.t = simulator.currentEvent.t;
                    }
                    else
                    {
                        double ratio = delay / event.dt;
                        int    step  = (int) Math.round (ratio);
                        if (Math.abs (ratio - step) < 1e-3)
                        {
                            if (simulator.during) spike = new EventSpikeMultiLatch ();
                            else                  spike = new EventSpikeMulti ();
                            delay = step * event.dt;
                        }
                        else
                        {
                            spike = new EventSpikeMulti ();
                        }
                        spike.t = simulator.currentEvent.t + delay;
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
                    simulator.queueEvent.add (spike);
                }
            }
        }

        // Finalize values and prepare for next cycle.
        if (bed.lastT != null) setFinal (bed.lastT, new Scalar (simulator.currentEvent.t));
        for (Variable v : bed.localBufferedExternal) setFinal (v, getFinal (v));
        clearExternalWriteBuffers (bed.localBufferedExternalWrite);
        for (Integer i : bed.eventLatches) valuesFloat[i] = 0;

        // $type split
        if (bed.type != null)
        {
            int type = (int) ((Scalar) get (bed.type)).value;
            if (type > 0)
            {
                ArrayList<EquationSet> split = equations.splits.get (type - 1);
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
                        InternalBackendData otherBed = (InternalBackendData) other.backendData;
                        Part p = new Part (other, (Part) container);  // zeroes all variables

                        // If this is a connection, keep the same bindings
                        Conversion conversion = bed.conversions.get (other);
                        if (conversion.bindings != null)
                        {
                            for (int j = 0; j < conversion.bindings.length; j++)
                            {
                                p.valuesObject[otherBed.endpoints+conversion.bindings[j]] = valuesObject[bed.endpoints+j];
                            }
                        }

                        event.enqueue (p);
                        p.resolve ();

                        // Copy over variables
                        int count = conversion.from.size ();
                        for (int v = 0; v < count; v++)
                        {
                            Variable from = conversion.from.get (v);
                            Variable to   = conversion.to  .get (v);
                            p.setFinal (to, get (from));
                        }
                        p.setFinal (otherBed.type, splitPosition);  // sets $type, which will appear during init cycle

                        p.init (simulator);
                    }
                }
                if (used)
                {
                    // We want the current value of $type to be the position of this existing part in the split.
                    // The next value of $type (applied during the next call to finish()) should default to 0.
                    // This can change during update().
                    set (bed.type, new Scalar (0));
                }
                else
                {
                    die ();
                    return false;
                }
            }
        }

        // Ways to die (other than $type split)
        if (equations.lethalP)
        {
            double p;
            if (bed.p.hasAttribute ("temporary"))
            {
                // Probe $p in run phase (as opposed to connect phase).
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator);
                for (Variable v : bed.PdependenciesTemp)
                {
                    Type result = v.eval (temp);
                    if (result == null) temp.set (v, v.type);
                    else                temp.set (v, result);
                }
                Type result = bed.p.eval (temp);
                if (result == null) p = 1;
                else                p = ((Scalar) result).value;
            }
            else
            {
                p = ((Scalar) get (bed.p)).value;
            }
            if (p <= 0  ||  p < 1  &&  Math.pow (p, event.dt) < simulator.random.nextDouble ())
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
            if (! ((Part) container).getLive ())
            {
                die ();
                return false;
            }
        }

        return true;
    }

    public void setPart (int i, Part p)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        valuesObject[bed.endpoints+i] = p;
    }

    public Part getPart (int i)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        return (Part) valuesObject[bed.endpoints+i];
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
            for (int i = 0; i < count; i++) if (! getPart (i).getLive ()) return false;
        }

        if (equations.lethalContainer)
        {
            if (! ((Part) container).getLive ()) return false;
        }

        return true;
    }

    public double getP (Simulator simulator)
    {
        // Probe $p in connect phase (as opposed to run phase).
        InstanceConnect temp = new InstanceConnect (this, simulator);
        if (temp.bed.p == null) return 1;  // N2A language defines default to be 1 (always create)
        for (Variable v : temp.bed.Pdependencies)
        {
            Type result = v.eval (temp);
            if (result == null) temp.set (v, v.type);
            else                temp.set (v, result);
        }
        Type result = temp.bed.p.eval (temp);
        if (result == null) return 1;
        return ((Scalar) result).value;
    }

    public double[] getXYZ (Simulator simulator, boolean connect)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.xyz == null) return new double[3];  // default is ~[0,0,0]
        if (! bed.xyz.hasAttribute ("temporary")) return ((MatrixDense) get (bed.xyz)).getData ();  // Either "constant" or stored

        InstanceTemporaries temp;
        List<Variable> list;
        if (connect)  // evaluate in connect phase
        {
            temp = new InstanceConnect (this, simulator);
            list = bed.XYZdependencies;
        }
        else  // evaluate in live phase
        {
            temp = new InstanceTemporaries (this, simulator);
            list = bed.XYZdependenciesTemp;
        }

        for (Variable v : list)
        {
            Type result = v.eval (temp);
            if (result == null) temp.set (v, v.type);
            else                temp.set (v, result);
        }
        Type result = bed.xyz.eval (temp);
        if (result == null) return new double[3];
        return ((MatrixDense) result).getData ();
    }

    public void checkInactive ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (! bed.connectionCanBeInactive) return;
        int populations = equations.parts.size ();
        for (int i = 0; i < populations; i++)
        {
            if (valuesObject[i] != null) return;
        }
        // All sub-populations of this connection instance are inactive, so this instance can be removed from simulation.
        die ();  // This should not affect anything important besides decreasing population.
        dequeue ();
        ((Population) container.valuesObject[bed.populationIndex]).checkInactive ();
    }

    public String path ()
    {
        String result = super.path ();
        if (equations.connectionBindings == null) return result;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.singleConnection) return result;

        // Connection doesn't have an index. Only way to get a unique name is to use its endpoints.
        result += "(" + ((Part) valuesObject[bed.endpoints]).path ();  // first endpoint
        int count = equations.connectionBindings.size ();
        for (int i = 1; i < count; i++) result += "-" + ((Part) valuesObject[bed.endpoints+i]).path ();  // subsequent endpoints
        return result + ")";
    }

    /**
        Supports connection matching to eliminate duplicates.
        Also supports exact object identity, so we can search event monitor lists.
    **/
    public boolean equals (Object o)
    {
        if (this == o) return true;  // exact object identity
        if (! (o instanceof Part)) return false;
        Part that = (Part) o;
        if (equations != that.equations) return false;  // Object identity is necessary here.
        if (equations.connectionBindings == null) return false;  // We are only interested in connection instances.
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int count = equations.connectionBindings.size ();
        for (int i = 0; i < count; i++) if (valuesObject[bed.endpoints+i] != that.valuesObject[bed.endpoints+i]) return false;  // Again, we use object identity. The endpoints must be exactly the same instance to match.
        return true;
    }

    /**
        Only supports connection matching to eliminate duplicates.
    **/
    public int hashCode ()
    {
        if (equations.connectionBindings == null) return super.hashCode ();
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int count = equations.connectionBindings.size ();
        int shift = 32 / count;
        int result = 0;
        for (int i = 0; i < count; i++)
        {
            Part p = (Part) valuesObject[bed.endpoints+i];
            InternalBackendData pbed = (InternalBackendData) p.equations.backendData;
            // Every connection endpoint, except for a singleton, should have an index.
            int index = 0;
            if (pbed.index != null) index = (int) p.valuesFloat[pbed.index.readIndex];
            result = (result << shift) + index;
        }
        return result;
    }
}
