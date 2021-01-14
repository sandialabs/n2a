/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Sat extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "sat";
            }

            public Operator createInstance ()
            {
                return new Sat ();
            }
        };
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;
        if (operands.length == 1)
        {
            Operator[] newOperands = new Operator[3];
            newOperands[0] = operands[0];
            newOperands[1] = new Constant (-1);
            newOperands[2] = new Constant ( 1);
            operands = newOperands;
        }
        else if (operands.length == 2  &&  operands[1].isScalar ())
        {
            Operator[] newOperands = new Operator[3];
            newOperands[0] = operands[0];
            newOperands[1] = new Constant (- operands[1].getDouble ());
            newOperands[2] = operands[1];
            operands = newOperands;
        }
        return this;
    }

    public Type eval (Instance context)
    {
        Type lower = operands[1].eval (context);
        Type upper;
        if (operands.length >= 3)
        {
            upper = operands[2].eval (context);
        }
        else
        {
            upper = lower;
            lower = lower.multiply (new Scalar (-1));
        }
        return operands[0].eval (context).max (lower).min (upper);
    }

    public String toString ()
    {
        return "sat";
    }
}
