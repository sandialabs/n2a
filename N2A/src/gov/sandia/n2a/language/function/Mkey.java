/*
Copyright 2023-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.List;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Mkey extends Mfile
{
    public boolean number;  // Indicates that "int" keyword was true. This is not allowed to change at run time.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "Mkey";
            }

            public Operator createInstance ()
            {
                return new Mkey ();
            }
        };
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator k = getKeyword ("number");
        if (k != null)
        {
            number =  k.getDouble () != 0;
            keywords.remove ("number");
        }

        return super.simplify (from, evalOnly);
    }

    public Type getType ()
    {
        if (number) return new Scalar ();
        return new Text ();
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return getType ();  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Holder H = Holder.get (simulator, path, context, this);
        List<String> childKeys = H.getChildKeys (context, this);

        int index = (int) ((Scalar) operands[1].eval (context)).value;
        String childKey = childKeys.get (index);
        if (! number) return new Text (childKey);
        try
        {
            return new Scalar (Double.valueOf (childKey));
        }
        catch (NumberFormatException e)
        {
            return new Scalar ();
        }
    }

    public String toString ()
    {
        return "Mkey";
    }
}
