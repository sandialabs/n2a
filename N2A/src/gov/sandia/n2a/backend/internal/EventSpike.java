package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;

public abstract class EventSpike extends Event
{
    EventTarget eventType;

    public void run (Euler simulator)
    {
    }

    public void setFlag ()
    {
    }
}
