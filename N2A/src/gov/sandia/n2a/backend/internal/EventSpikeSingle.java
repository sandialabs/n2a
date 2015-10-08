package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.type.Instance;

public class EventSpikeSingle extends EventSpike
{
    Instance target;

    public void run (Euler simulator)
    {
        setFlag ();
        simulator.integrate (target);
        target.update (simulator);
        if (! target.finish (simulator)) target.dequeue ();
    }

    public void setFlag ()
    {
        target.valuesFloat[eventType.valueIndex] = (float) ((int) target.valuesFloat[eventType.valueIndex] | eventType.mask);
    }
}
