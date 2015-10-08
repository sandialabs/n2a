package gov.sandia.n2a.backend.internal;

public class EventSpikeMultiLatch extends EventSpikeMulti
{
    public void run (Euler simulator)
    {
        setFlag ();
        // Note absence of normal cycle processing.
    }
}
