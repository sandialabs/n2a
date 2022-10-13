/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class DrawSegment extends Draw implements Draw.Shape
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
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        boolean raw = applyKeywords (context, H);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        Matrix p = (Matrix) operands[1].eval (context);
        double x = p.get (0);
        double y = p.get (1);

        p = (Matrix) operands[2].eval (context);
        double x2 = p.get (0);
        double y2 = p.get (1);

        double width = 0;
        if (operands.length > 3) width = ((Scalar) operands[3].eval (context)).value;

        double color = 0xFFFFFF;  // white
        if (operands.length > 4) color = ((Scalar) operands[4].eval (context)).value;

        H.drawSegment (now, raw, x, y, x2, y2, width, (int) color);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawSegment";
    }
}
