/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class DrawSegment extends Draw
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawSegment";
            }

            public Operator createInstance ()
            {
                return new DrawSegment ();
            }
        };
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        double x     = ((Scalar) operands[1].eval (context)).value;
        double y     = ((Scalar) operands[2].eval (context)).value;
        double x2    = ((Scalar) operands[3].eval (context)).value;
        double y2    = ((Scalar) operands[4].eval (context)).value;
        double width = ((Scalar) operands[5].eval (context)).value;
        double color = ((Scalar) operands[6].eval (context)).value;
        H.drawSegment (now, x, y, x2, y2, width, (int) color);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawSegment";
    }
}
