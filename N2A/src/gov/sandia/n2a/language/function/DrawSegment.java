/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
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

    public Operator simplify (Variable from, boolean evalOnly)
    {
        super.simplify (from, evalOnly);  // We can't be a constant, so won't be replaced.
        if (operands.length < 5)
        {
            Operator[] nextOperands = new Operator[4];
            nextOperands[0] = operands[0];
            nextOperands[1] = operands[1];
            nextOperands[2] = operands[2];
            if (operands.length > 3) nextOperands[3] = operands[3];
            else                     nextOperands[3] = new Constant (0);  // width is 1px
            nextOperands[4] = new Constant (0xFFFFFF);  // white
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

        p = (Matrix) operands[2].eval (context);
        double x2 = p.get (0);
        double y2 = p.get (1);

        double width = ((Scalar) operands[3].eval (context)).value;
        double color = ((Scalar) operands[4].eval (context)).value;

        H.drawSegment (now, raw, x, y, x2, y2, width, (int) color);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawSegment";
    }
}
