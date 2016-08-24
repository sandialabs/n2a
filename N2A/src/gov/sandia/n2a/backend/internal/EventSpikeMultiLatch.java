package gov.sandia.n2a.backend.internal;

public class EventSpikeMultiLatch extends EventSpikeMulti
{
    public void run (Simulator simulator)
    {
        setFlag ();
        // Note absence of normal cycle processing.
    }
}
