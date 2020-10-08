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
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        Matrix p = (Matrix) operands[1].eval (context);
        double x = p.get (0);
        double y = p.get (1);

        double radius = 0;
        if (operands.length > 2) radius = ((Scalar) operands[2].eval (context)).value;

        double color = 0xFFFFFF;  // white
        if (operands.length > 3) color = ((Scalar) operands[3].eval (context)).value;

        H.drawDisc (now, x, y, radius, (int) color);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawDisc";
    }
}
