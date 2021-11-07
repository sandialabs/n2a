/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class UnitMap extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "unitmap";
            }

            public Operator createInstance ()
            {
                return new UnitMap ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++)
        {
            operands[i].determineExponent (context);
        }
        Operator A = operands[0];  // The matrix
        updateExponent (context, A.exponent, A.center);
    }

    public void determineExponentNext ()
    {
        // See AccessElement for similar code ...
        Operator v = operands[0];
        if (v instanceof Constant  ||  v instanceof AccessVariable)  // The preferred use of this function is to access a non-calculated matrix.
        {
            v.exponentNext = v.exponent;  // Let matrix output in its natural exponent.
            exponent       = v.exponent;  // Force our output to be shifted after the fact.
        }
        else  // A matrix expression of some sort, so pass the required exponent on to it. This case should be rare.
        {
            v.exponentNext = exponentNext;  // Pass the required exponent on to the expression
            exponent       = exponentNext;  // and expect that we require not further adjustment.
        }
        v.determineExponentNext ();

        // Other operands are either float coordinates or a mode string. In the latter case, it doesn't matter what exponent we set.
        for (int i = 1; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = 0;    // a number in [0,1] with some provision for going slightly out of bounds
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++)
        {
            operands[i].determineUnit (fatal);
        }
        unit = operands[0].unit;
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Matrix A =  (Matrix) operands[0].eval (context);
        double r = ((Scalar) operands[1].eval (context)).value;
        double c = 0.5;
        if (operands.length > 2)
        {
            Type op2 = operands[2].eval (context);
            if (op2 instanceof Scalar) c = ((Scalar) op2).value;
        }
        return new Scalar (A.get (r, c, Matrix.UNITMAP));
    }

    public String toString ()
    {
        return "unitmap";
    }
}
