/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;

public class Event extends Function
{
    public EventTarget eventType;  // If another event in the same part shares the same parameters, it will have the same EventTarget.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "event";
            }

            public Operator createInstance ()
            {
                return new Event ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineExponent (context);

        // Our output is boolean, so use standard values for that.
        int cent = MSB / 2;
        int pow  = -cent;
        updateExponent (context, pow, cent);

        // Need to record the exponent for time, since we avoid passing context to determineExponentNext().
        if (operands.length > 1) operands[1].exponentNext = context.exponentTime;
    }

    public void determineExponentNext ()
    {
        Operator cond = operands[0];
        cond.exponentNext = cond.exponent;  // exponentNext determines the magnitude of the temporary used to detect changes.
        cond.determineExponentNext ();

        if (operands.length > 1)
        {
            Operator time = operands[1];
            // time.exponentNext already set in determineExponent()
            time.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        if (eventType != null)  // This check is necessary because eval() can be called outside of Internal simulator.
        {
            if (context instanceof InstanceTemporaries) context = ((InstanceTemporaries) context).wrapped;  // event latches are always stored in main instance, never in a temporary variable
            if (eventType.getLatch (context)) return new Scalar (1);
        }
        return new Scalar (0);
    }

    public String toString ()
    {
        return "event";
    }
}
