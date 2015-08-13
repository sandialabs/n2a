package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.EvaluationException;

public class Event implements Comparable<Event>
{
    public double t = 0;

    public void run (Euler simulator)
    {
        throw new EvaluationException ("Event is abstract");
    }

    public int compareTo (Event that)
    {
        if (t > that.t) return 1;
        if (t < that.t) return -1;
        return 0;
    }
}
