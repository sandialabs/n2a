/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
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

    public void determineExponent (ExponentContext context)
    {
        // By construction, we can't get an exponent hint.
        // TODO: when named parameters are supported, use "median=hint"
        int centerNew   = MSB / 2;
        int exponentNew = MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        exponent = exponentNext;  // Conversion done while reading.
        // All our operands are strings, so no point in passing the exponent down.
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
        Holder H = Holder.get (simulator, path);
        return new Scalar (H.doc.getDouble (Holder.keyPath (context, operands)));
    }

    public String toString ()
    {
        return "Mnumber";
    }
}
