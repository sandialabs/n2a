/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

public class Mcount extends Mfile
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "Mcount";
            }

            public Operator createInstance ()
            {
                return new Mcount ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        updateExponent (context, 0, 0);  // small integer
    }

    public void determineExponentNext ()
    {
        // Don't change our own exponent. We always output an integer.

        for (int i = 1; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = 0;
            op.determineExponentNext ();
        }
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
        String[] keyPath = Holder.keyPath (context, this, 1);
        return new Scalar (H.doc.childOrEmpty (keyPath).size ());
    }

    public String toString ()
    {
        return "Mcount";
    }
}
