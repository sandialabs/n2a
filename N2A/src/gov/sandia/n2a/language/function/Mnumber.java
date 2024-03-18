/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Mnumber extends Mfile
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "Mnumber";
            }

            public Operator createInstance ()
            {
                return new Mnumber ();
            }
        };
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return getType ();  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Holder H = Holder.get (simulator, path, context, this);
        return new Scalar (H.doc.getDouble (Holder.keyPath (context, this, 1)));
    }

    public String toString ()
    {
        return "Mnumber";
    }
}
