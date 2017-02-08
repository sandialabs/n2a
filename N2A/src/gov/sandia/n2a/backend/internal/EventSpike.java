package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;

public abstract class EventSpike extends Event
{
    public EventTarget eventType;

    public void run (Simulator simulator)
    {
    }

    public void setFlag ()
    {
    }
}
