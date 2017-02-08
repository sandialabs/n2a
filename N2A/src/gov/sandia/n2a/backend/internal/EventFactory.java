package gov.sandia.n2a.backend.internal;

public class EventFactory
{
    public EventStep create (double t, double dt)
    {
        return new EventStep (t, dt);
    }
}
