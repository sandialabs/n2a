/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;

public class Modulo extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "%";
            }

            public Operator createInstance ()
            {
                return new Modulo ();
            }
        };
    }

    public int precedence ()
    {
        return 4;
    }

    public void determineExponent (Variable from)
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);
        updateExponent (from, operand1.exponent);
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).modulo (operand1.eval (context));
    }

    public String toString ()
    {
        return "%";
    }
}
