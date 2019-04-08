/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
        return 0;
    }
}
