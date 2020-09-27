/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;
import gov.sandia.n2a.language.type.Matrix;

public class Atan extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "atan";
            }

            public Operator createInstance ()
            {
                return new Atan ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineExponent (context);
        updateExponent (context, 1, MSB - 1);  // in [-pi,pi]
    }

    public void determineExponentNext ()
    {
        Operator y = operands[0];
        int next;
        if (operands.length == 1)
        {
            if (y.getType () instanceof Matrix)
            {
                next = y.exponent;
            }
            else
            {
                // atan(y) = atan2(y,1), so treat x as 1
                // If y is so small that all its bits are lost, then angle can be treated as zero.
                // If y is so large that all the bits of x are lost, then angle can be treated as pi. 
                next = Math.max (0, y.exponent);
            }
        }
        else
        {
            Operator x = operands[1];
            next = Math.max (x.exponent, y.exponent);
            x.exponentNext = next;
            x.determineExponentNext ();
        }
        y.exponentNext = next;
        y.determineExponentNext ();

    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++)
        {
            operands[i].determineUnit (fatal);
        }
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        double y;
        double x;
        Type arg = operands[0].eval (context);
        if (arg instanceof Matrix)
        {
            x = ((Matrix) arg).get (0);
            y = ((Matrix) arg).get (1);
        }
        else  // arg must be Scalar; otherwise the casts below will throw an exception.
        {
            y = ((Scalar) arg).value;
            if (operands.length == 1) return new Scalar (Math.atan (y));

            x = ((Scalar) operands[1].eval (context)).value;
        }

        return new Scalar (Math.atan2 (y, x));
    }

    public void solve (Equality statement) throws EvaluationException
    {
        statement.lhs = operands[0];
        Tangent tan = new Tangent ();
        tan.operands = new Operator[1];
        tan.operands[0] = statement.rhs;
        statement.rhs = tan;
    }

    public String toString ()
    {
        return "atan";
    }
}
