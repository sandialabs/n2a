package gov.sandia.n2a.backend.internal;

public class EventSpikeSingleLatch extends EventSpikeSingle
{
    public void run (Simulator simulator)
    {
        setFlag ();
        // Note absence of normal cycle processing.
    }
}
