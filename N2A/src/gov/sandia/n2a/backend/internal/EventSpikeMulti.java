package gov.sandia.n2a.backend.internal;

import java.util.List;

import gov.sandia.n2a.language.type.Instance;

public class EventSpikeMulti extends EventSpike
{
    public List<Instance> targets;

    public void run (Simulator simulator)
    {
        setFlag ();
        for (Instance i : targets) simulator.integrate (i);
        for (Instance i : targets) i.update (simulator);
        for (Instance i : targets) if (! i.finish (simulator)) i.dequeue ();
    }

    public void setFlag ()
    {
        for (Instance i : targets) eventType.setLatch (i);
    }
}
