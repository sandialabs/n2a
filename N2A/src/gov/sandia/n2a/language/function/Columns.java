/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Columns extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "columns";
            }

            public Operator createInstance ()
            {
                return new Columns ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        updateExponent (context, MSB, 0);  // small integer
    }

    public void determineExponentNext ()
    {
        // No action. The operand is never evaluated.
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Matrix A = (Matrix) operands[0].eval (context);
        return new Scalar (A.columns ());
    }

    public String toString ()
    {
        return "columns";
    }
}
