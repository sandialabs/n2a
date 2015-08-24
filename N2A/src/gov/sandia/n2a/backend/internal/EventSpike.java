package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.language.type.Instance;

public class EventSpike extends Event
{
    Instance target;
    int      index;  // identifier for this specific event

    public void run (Euler simulator)
    {
        setFlag ();
        simulator.integrate (target);
        target.prepare ();  // TODO: eliminate prepare() phase. It is incompatible with mixed-frequency events and with spiking.
        target.update (simulator);
        if (! target.finish (simulator)) target.dequeue ();
    }

    public void setFlag ()
    {
        InternalBackendData bed = (InternalBackendData) target.equations.backendData;
        EventTarget et = bed.eventTargets.get (index);
        target.valuesFloat[et.valueIndex] = (float) ((int) target.valuesFloat[et.valueIndex] | et.mask);
    }
}
