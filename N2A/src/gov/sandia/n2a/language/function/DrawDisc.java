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
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class DrawDisc extends Draw
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawDisc";
            }

            public Operator createInstance ()
            {
                return new DrawDisc ();
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
        double radius;
        if (operands.length > i) radius = ((Scalar) operands[i++].eval (context)).value;
        else                     radius = 0;
        double color;
        if (operands.length > i) color = ((Scalar) operands[i].eval (context)).value;
        else                     color = 0xFFFFFF;  // white
        H.drawDisc (now, x, y, radius, (int) color);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawDisc";
    }
}
