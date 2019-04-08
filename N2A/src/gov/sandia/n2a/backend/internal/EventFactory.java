/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

public class EventFactory
{
    public EventStep create (double t, double dt)
    {
        return new EventStep (t, dt);
    }
}
