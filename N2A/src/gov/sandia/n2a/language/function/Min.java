/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class Min extends Function
{
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

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        // Check if Min appears as an operand. If so, merge its operands into ours
        ArrayList<Operator> newOperands = new ArrayList<Operator> (operands.length);
        boolean changed = false;
        for (int i = 0; i < operands.length; i++)
        {
            Operator o = operands[i];
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
            newMin.operands = newOperands.toArray (new Operator[0]);
            return result;
        }

        return this;
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
