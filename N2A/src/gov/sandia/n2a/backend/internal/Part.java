/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;

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
        if (equations.parts.size () > 0)
        {
            int i = 0;
            for (EquationSet s : equations.parts)
            {
                s.priority = i;  // For anti-indexing into valuesObject during init
                valuesObject[i++] = new Population (s, this);
            }
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
        // set $live to false, if it is stored in this part
        InternalBackendData bed = (InternalBackendData) equations.backendData;
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
        for (Variable v : bed.localInit)
        {
            Type result = v.eval (temp);
            if (result == null  ||  v.reference.variable.writeIndex < 0) continue;
            if (v.reference.variable == v) temp.setFinal (v, result);
            // else this is an external reference, so need to do a proper combining operation in the target part. The options are:
            // 1) combine with buffered value
            // 2) combine with final value
            // 3) do nothing
            // Option 1 is definitely wrong, because it can cause a value to be added twice (here and in update) before the next call to finish.
            // Option 2 is dubious, because it could cause changes to a value in the middle of a cycle.
            // That leaves option 3, which is what we take here. This means that a combiner generally has no effect during the init cycle,
            // and must wait until the first full cycle to deliver its result.
        }
        if (bed.liveStorage == InternalBackendData.LIVE_STORED) set (bed.live, new Scalar (1));
        if (bed.lastT != null) temp.setFinal (bed.lastT, new Scalar (simulator.currentEvent.t));
        if (bed.type != null) temp.setFinal (bed.type, new Scalar (0));

        // zero external buffered variables that may be written before first finish()
        clearExternalWriteBuffers (bed.localBufferedExternalWrite);
        for (Variable v : bed.localBufferedExternalWrite) if (v.assignment == Variable.REPLACE) temp.set (v, temp.get (v));

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

        if (equations.parts.size () > 0)
        {
            // If there are parts at all, then orderedParts must be filled in correctly. Otherwise it may be null.
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

        for (int i = 0; i < populations; i++) ((Population) valuesObject[i]).integrate (simulator, dt);
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
        for (int i = 0; i < populations; i++) ((Population) valuesObject[i]).update (simulator);
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        int populations = equations.parts.size ();
        for (int i = 0; i < populations; i++) ((Population) valuesObject[i]).finish (simulator);

        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            if (((Scalar) get (bed.live)).value == 0) return false;  // early-out if we are already dead, to avoid another call to die()
        }

        // Events
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
                            if (simulator.eventMode == Simulator.DURING) spike = new EventSpikeSingleLatch ();
                            else                                         spike = new EventSpikeSingle ();
                            if (simulator.eventMode == Simulator.AFTER) delay = (step + 1e-6) * event.dt;
                            else                                        delay = (step - 1e-6) * event.dt;
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
                                if (simulator.eventMode == Simulator.DURING) spike = new EventSpikeSingleLatch ();
                                else                                         spike = new EventSpikeSingle ();
                                if (simulator.eventMode == Simulator.AFTER) delay = (step + 1e-6) * event.dt;
                                else                                        delay = (step - 1e-6) * event.dt;
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
                            if (simulator.eventMode == Simulator.DURING) spike = new EventSpikeMultiLatch ();
                            else                                         spike = new EventSpikeMulti ();
                            if (simulator.eventMode == Simulator.AFTER) delay = (step + 1e-6) * event.dt;
                            else                                        delay = (step - 1e-6) * event.dt;
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

        // Other stuff
        if (bed.lastT != null) setFinal (bed.lastT, new Scalar (simulator.currentEvent.t));
        for (Variable v : bed.localBufferedExternal) setFinal (v, getFinal (v));
        clearExternalWriteBuffers (bed.localBufferedExternalWrite);
        for (Integer i : bed.eventLatches) valuesFloat[i] = 0;

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
                if (! used)
                {
                    die ();
                    return false;
                }
            }
        }

        if (equations.lethalP)
        {
            double p;
            if (bed.p.hasAttribute ("temporary"))
            {
                // Probe $p in run phase (as opposed to connect phase).
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator);
                for (Variable v : bed.Pdependencies)
                {
                    Type result = v.eval (temp);
                    if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
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
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        Type result = temp.bed.p.eval (temp);
        if (result == null) return 1;
        return ((Scalar) result).value;
    }

    public double[] getXYZ (Simulator simulator, boolean connect)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.xyz == null) return new double[3];  // default is ~[0,0,0]
        if (! bed.xyz.hasAttribute ("temporary")) return ((MatrixDense) get (bed.xyz)).getRawColumn (0);  // Either "constant" or stored

        InstanceTemporaries temp;
        if (connect) temp = new InstanceConnect     (this, simulator);
        else         temp = new InstanceTemporaries (this, simulator);

        for (Variable v : bed.XYZdependencies)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        Type result = bed.xyz.eval (temp);
        if (result == null) return new double[3];
        return ((MatrixDense) result).getRawColumn (0);
    }

    public String path ()
    {
        String result = super.path ();
        if (equations.connectionBindings == null) return result;

        // For connections, it is more understandable to show our endpoints rather than our own name.
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        result += "(" + ((Part) valuesObject[bed.endpoints]).path ();  // first endpoint
        int count = equations.connectionBindings.size ();
        for (int i = 1; i < count; i++) result += "-" + ((Part) valuesObject[bed.endpoints+i]).path ();  // other endpoints
        return result + ")";
    }

    /**
        Only supports connection matching to eliminate duplicates.
    **/
    public boolean equals (Object o)
    {
        if (! (o instanceof Part)) return false;
        Part that = (Part) o;
        if (equations.connectionBindings == null  ||  that.equations.connectionBindings == null) return false;  // We are only interested in connection instances.
        if (equations != that.equations) return false;  // Object identity is necessary here.
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
            // Every connection endpoint should have an index, at least for Internal backend.
            int index = (int) p.valuesFloat[pbed.index.readIndex];
            result = (result << shift) + index;
        }
        return result;
    }
}
