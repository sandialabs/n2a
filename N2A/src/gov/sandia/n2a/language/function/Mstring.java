/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Text;

public class Mstring extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "Mstring";
            }

            public Operator createInstance ()
            {
                return new Mstring ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public boolean canBeInitOnly ()
    {
        return true;
    }

    public void determineExponent (ExponentContext context)
    {
        // Leave as unknown
    }

    public void determineExponentNext ()
    {
        // We don't have an exponent.
        // Furthermore, all our operands are strings, so no point in passing the exponent down.
    }

    public Type getType ()
    {
        return new Text ();
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return getType ();  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Mmatrix.Holder H = Mmatrix.Holder.get (simulator, path);
        return new Text (H.doc.get (Mmatrix.Holder.keyPath (context, operands)));
    }

    public String toString ()
    {
        return "Mstring";
    }
}
