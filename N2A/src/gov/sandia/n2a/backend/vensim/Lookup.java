/*
Copyright 2019-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Lookup extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "lookup";
            }

            public Operator createInstance ()
            {
                return new Lookup ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        operands[0].determineExponent (context);
        operands[1].determineExponent (context);
        Operator op1 = operands[1];
        updateExponent (context, op1.exponent, op1.center);
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operands[0].determineUnit (fatal);
        operands[1].determineUnit (fatal);
        unit = operands[1].unit;
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        double x = ((Scalar) operands[0].eval (context)).value;
        Matrix A =  (Matrix) operands[1].eval (context);  // Format: rows are tuples, column 0 is domain value, and column 1 is range value.
        // TODO: Add other extrapolation modes. Third parameter is a string which indicates which one.
        int rows = A.rows ();
        double x0 = A.get (0, 0);
        if (x < x0) return new Scalar (A.get (0, 1));
        for (int i = 1; i < rows; i++)
        {
            double x1 = A.get (i, 0);
            if (x0 <= x  &&  x < x1)
            {
                double y0 = A.get (i-1, 1);
                double y1 = A.get (i,   1);
                return new Scalar (y0 + (y1 - y0) * (x - x0) / (x1 - x0));
            }
            x0 = x1;
        }
        return new Scalar (A.get (rows-1, 1));
    }

    public String toString ()
    {
        return "lookup";
    }
}
