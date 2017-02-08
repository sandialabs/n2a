package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.type.Instance;

public class EventSpikeSingle extends EventSpike
{
    Instance target;

    public void run (Simulator simulator)
    {
        setFlag ();
        simulator.integrate (target);
        target.update (simulator);
        if (! target.finish (simulator)) target.dequeue ();
    }

    public void setFlag ()
    {
        eventType.setLatch (target);
    }
}
