/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.TreeMap;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class DrawLight extends Draw
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawLight";
            }

            public Operator createInstance ()
            {
                return new DrawLight ();
            }
        };
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        applyKeywords (context, H);

        // Lights function much like generic draw. They stage info until start of next cycle.

        int index = (int) ((Scalar) operands[1].eval (context)).value;
        if (H.lights == null) H.lights = new TreeMap<Integer,Light> ();
        if (evalKeyword (context, "on", true))
        {
            Light light = new Light ();
            light.index = index;
            H.lights.put (index, light);
            light.extract (this, context);
        }
        else
        {
            H.lights.remove (index);
        }

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawLight";
    }
}
