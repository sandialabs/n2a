/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.ArrayList;
import java.util.Arrays;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Min extends Function
{
    protected Type type;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "min";
            }

            public Operator createInstance ()
            {
                return new Min ();
            }
        };
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        // Check if Min appears as an operand. If so, merge its operands into ours
        ArrayList<Operator> newOperands = new ArrayList<Operator> (operands.length);
        boolean changed = false;
        for (Operator o : operands)
        {
            if (o instanceof Min)
            {
                Min m = (Min) o;
                newOperands.addAll (Arrays.asList (m.operands));
                changed = true;
            }
            else
            {
                newOperands.add (o);
            }
        }
        if (changed)
        {
            from.changed = true;
            Min newMin = new Min ();
            newMin.parent = parent;
            newMin.operands = newOperands.toArray (new Operator[0]);
            for (Operator o : newMin.operands) o.parent = newMin;
            return result;
        }

        return this;
    }

    public Type getType ()
    {
        if (type != null) return type;
        type = new Scalar ();
        for (Operator op : operands)
        {
            Type a = op.getType ();
            if (a.betterThan (type)) type = a;
        }
        return type;
    }

    public Type eval (Instance context)
    {
        Type result = operands[0].eval (context);
        for (int i = 1; i < operands.length; i++) result = result.min (operands[i].eval (context));
        return result;
    }

    public String toString ()
    {
        return "min";
    }
}
