/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import gov.sandia.n2a.backend.internal.InternalBackendData.Conversion;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventSource;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;

/**
    An Instance that is capable of holding sub-populations.
    Generally, this is any kind of Instance except a Population.
**/
public class Part extends Instance
{
    public Population[] populations;
    public EventStep    event;     // Every Part lives on some simulation queue, held by an EventStep object.
    public Part         next;      // simulation queue
    public Part         previous;  // simulation queue
    public Part         before;    // population management
    public Part         after;     // population management

    /**
        Empty constructor, specifically for use by Wrapper and EventStep.
    **/
    public Part ()
    {
    }

    public Part (EquationSet equations, Population container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countLocalFloat, bed.countLocalObject);
        if (equations.parts.size () > 0)
        {
            populations = new Population[equations.parts.size ()];
            int i = 0;
            for (EquationSet s : equations.parts) populations[i++] = new Population (s, this);
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

        // update accountable endpoints
        if (bed.accountableEndpoints != null)
        {
            int count = bed.accountableEndpoints.length;
            for (int i = 0; i < count; i++)
            {
                Variable ae = bed.accountableEndpoints[i];
                if (ae != null)
                {
                    Part p = (Part) valuesObject[bed.endpoints+i];
                    Scalar m = (Scalar) p.get (ae);
                    m.value--;
                    p.set (ae, m);
                }
            }
        }

        ((Population) container).remove (this);
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
        Note: specifically for Parts, call resolve() before calling init(). This is to
        accommodate the connection process, which must probe values in a part (which
        may include references) before calling init().
    **/
    public void init (Simulator simulator)
    {
        ((Population) container).insert (this);  // update $n and assign $index
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);

        // update accountable endpoints
        // Note: these do not require resolve(). Instead, they access their target directly through the endpoints array.
        if (temp.bed.accountableEndpoints != null)
        {
            int count = temp.bed.accountableEndpoints.length;
            for (int i = 0; i < count; i++)
            {
                Variable ae = temp.bed.accountableEndpoints[i];
                if (ae != null)
                {
                    Part p = (Part) valuesObject[temp.bed.endpoints+i];
                    Scalar m = (Scalar) p.get (ae);
                    m.value++;
                    p.set (ae, m);
                }
            }
        }

        // $variables
        if (temp.bed.liveStorage == InternalBackendData.LIVE_STORED) set (temp.bed.live, new Scalar (1));  // force $live to be set before anything else
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

        // Request event monitors
        for (EventTarget et : temp.bed.eventTargets)
        {
            for (Entry<EquationSet,EventSource> i : et.sources.entrySet ())
            {
                EventSource es = i.getValue ();
                Part source;
                if (es.reference == null) source = this;
                else                      source = (Part) valuesObject[es.reference.index];
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
            if (v.reference.variable.writeIndex < 0) continue;  // this is a "dummy" variable, so calling eval() was all we needed to do

            if (result == null)  // no condition matched
            {
                if (v.reference.variable != v  ||  v.equations.size () == 0) continue;
                if (v.readIndex == v.writeIndex)  // not buffered
                {
                    if (v.readTemp) temp.set (v, v.type);  // This is a pure temporary for which no equation fired, so set value to default for use by later equations. Note that readTemp==writeTemp==true.
                    continue;
                }

                // Variable is buffered
                if (v.assignment == Variable.REPLACE)  // not an accumulator
                {
                    temp.set (v, temp.get (v));  // so copy its value
                }
                continue;
            }
            // Note: $type is explicitly evaluated to 0 in Variable.eval(), so it never returns null, even when no conditions match.

            if (v.assignment == Variable.REPLACE)
            {
                temp.set (v, result);
            }
            else
            {
                // the rest of these require knowing the current value of the working result, which is most likely external buffered
                Type current = temp.getFinal (v.reference);
                switch (v.assignment)
                {
                    case Variable.ADD:      temp.set (v, current.add      (result)); break;
                    case Variable.MULTIPLY: temp.set (v, current.multiply (result)); break;
                    case Variable.DIVIDE:   temp.set (v, current.divide   (result)); break;
                    case Variable.MIN:      temp.set (v, current.min      (result)); break;
                    case Variable.MAX:      temp.set (v, current.max      (result)); break;
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

        if (populations != null) for (Population p : populations) p.finish (simulator);

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
                        // Is delay an quantum number of $t' steps?
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
                    simulator.eventQueue.add (spike);
                }
            }
            else  // All monitors share same condition, so only test one.
            {
                double delay = eventType.test (monitors.get (0), simulator);
                if (delay < -1) continue;  // the trigger condition was not satisfied

                if (es.delayEach)  // Each target instance may require a different delay.
                {
                    for (Instance i : monitors)
                    {
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
                        simulator.eventQueue.add (spike);
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
                    simulator.eventQueue.add (spike);
                }
            }
        }

        // Other stuff
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
                            InternalBackendData otherBed = (InternalBackendData) other.backendData;
                            Population otherPopulation = ((Part) container.container).populations[otherBed.populationIndex];
                            Part p = new Part (other, otherPopulation);

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
                            p.init (simulator);  // accountable connections are updated here

                            // Copy over variables
                            int count = conversion.from.size ();
                            for (int v = 0; v < count; v++)
                            {
                                Variable from = conversion.from.get (v);
                                Variable to   = conversion.to  .get (v);
                                p.setFinal (to, get (from));
                            }

                            // Set $type to be our position in the split
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
            if (p == 0  ||  p < 1  &&  p < simulator.random.nextDouble ())
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

    public int getCount (int i)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        Variable ae = bed.accountableEndpoints[i];
        if (ae == null) return 0;
        return (int) ((Scalar) ((Part) valuesObject[bed.endpoints+i]).get (ae)).value;
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
            if (! ((Part) container.container).getLive ()) return false;
        }

        return true;
    }

    public double getP (Simulator simulator)
    {
        InstancePreLive temp = new InstancePreLive (this, simulator);
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
        return new MatrixDense (3, 1);  // default is ~[0,0,0]
    }

    public Matrix project (int i, int j)
    {
        // TODO: as part of spatial filtering, implement project()
        return new MatrixDense (3, 1);
    }

    public String path ()
    {
        if (equations.connectionBindings == null) return super.path ();

        // For connections, it is more understandable to show our endpoints rather than our own name.
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        String result = ((Part) valuesObject[bed.endpoints]).path ();
        int count = equations.connectionBindings.size ();
        for (int i = 1; i < count; i++) result += "-" + ((Part) valuesObject[bed.endpoints+i]).path ();
        return result;
    }
}
