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

public class Max extends Function
{
    protected Type type;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "max";
            }

            public Operator createInstance ()
            {
                return new Max ();
            }
        };
    }

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        // Check if Max appears as an operand. If so, merge its operands into ours
        ArrayList<Operator> newOperands = new ArrayList<Operator> (operands.length);
        boolean changed = false;
        for (Operator o : operands)
        {
            if (o instanceof Max)
            {
                Max m = (Max) o;
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
            Max newMax = new Max ();
            newMax.parent = parent;
            newMax.operands = newOperands.toArray (new Operator[0]);
            for (Operator o : newMax.operands) o.parent = newMax;
            return newMax;
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
        for (int i = 1; i < operands.length; i++) result = result.max (operands[i].eval (context));
        return result;
    }

    public String toString ()
    {
        return "max";
    }
}
