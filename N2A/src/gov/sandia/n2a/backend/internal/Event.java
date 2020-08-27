/*
Copyright 2018-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.EvaluationException;

public class Event implements Comparable<Event>
{
    public double t;

    public void run (Simulator simulator)
    {
        throw new EvaluationException ("Event is abstract");
    }

    public int compareTo (Event that)
    {
        if (t > that.t) return 1;
        if (t < that.t) return -1;
        // Events have the same timestamp, so sort by event type ...
        boolean stepA =  this instanceof EventStep;
        boolean stepB =  that instanceof EventStep;
        if (stepA  &&  stepB) return 0;  // Both are EventStep, so no-care about order.
        if (stepA) return - Simulator.instance.get ().sortEvent;
        if (stepB) return   Simulator.instance.get ().sortEvent;
        return 0;  // Neither is an EventStep, so no-care about order.
    }
}
