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
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        int i = 1;
        double x;
        double y;
        Type t = operands[i++].eval (context);
        if (t instanceof Matrix)
        {
            x = ((Matrix) t).get (0);
            y = ((Matrix) t).get (1);
        }
        else
        {
            x = ((Scalar) t).value;
            y = ((Scalar) operands[i++].eval (context)).value;
        }
        double x2;
        double y2;
        t = operands[i++].eval (context);
        if (t instanceof Matrix)
        {
            x2 = ((Matrix) t).get (0);
            y2 = ((Matrix) t).get (1);
        }
        else
        {
            x2 = ((Scalar) t).value;
            y2 = ((Scalar) operands[i++].eval (context)).value;
        }
        double width;
        if (operands.length > i) width = ((Scalar) operands[i++].eval (context)).value;
        else                     width = 0;
        double color;
        if (operands.length > i) color = ((Scalar) operands[i  ].eval (context)).value;
        else                     color = 0xFFFFFF;  // white
        H.drawSegment (now, x, y, x2, y2, width, (int) color);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawSegment";
    }
}
