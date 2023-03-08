/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class DrawSphere extends Draw implements Draw.Shape
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawSphere";
            }

            public Operator createInstance ()
            {
                return new DrawSphere ();
            }
        };
    }

    public static void draw (Holder H, Matrix p, double r)
    {
        
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        applyKeywords (context, H);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        Matrix p =  (Matrix) operands[1].eval (context);
        double r = ((Scalar) operands[2].eval (context)).value;

        H.next (now);
        H.next3D ();
        draw (H, p, r);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawSphere";
    }
}
