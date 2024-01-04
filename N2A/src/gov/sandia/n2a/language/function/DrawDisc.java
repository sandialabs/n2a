/*
Copyright 2019-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.awt.Color;
import java.awt.geom.Ellipse2D;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class DrawDisc extends Draw2D
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

    public Operator simplify (Variable from, boolean evalOnly)
    {
        super.simplify (from, evalOnly);  // We can't be a constant, so won't be replaced.
        if (operands.length < 4)
        {
            Operator[] nextOperands = new Operator[4];
            nextOperands[0] = operands[0];
            nextOperands[1] = operands[1];
            if (operands.length > 2) nextOperands[2] = operands[2];
            else                     nextOperands[2] = new Constant (0);  // 1px dot
            nextOperands[3] = new Constant (0xFFFFFF);  // white
            operands = nextOperands;
        }
        return this;
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

        double radius = ((Scalar) operands[2].eval (context)).value;
        double color  = ((Scalar) operands[3].eval (context)).value;

        H.next (now);

        if (! raw)
        {
            x      *= H.width;
            y      *= H.width;
            radius *= H.width;
        }
        if (radius < 0.5) radius = 0.5;  // 1px diameter

        double w = 2 * radius;
        if (H.disc == null) H.disc = new Ellipse2D.Double (x - radius, y - radius, w, w);
        else                H.disc.setFrame               (x - radius, y - radius, w, w);

        H.graphics.setColor (new Color ((int) color));
        H.graphics.fill (H.disc);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawDisc";
    }
}
