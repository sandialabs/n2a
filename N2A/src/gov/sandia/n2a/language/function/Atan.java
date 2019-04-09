/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tec.uom.se.AbstractUnit;
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

    public void determineExponent (Variable from)
    {
        for (int i = 0; i < operands.length; i++)
        {
            operands[i].determineExponent (from);
        }
        updateExponent (from, 1, MSB - 1);  // in [-pi,pi]
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
        else if (arg instanceof Scalar)
        {
            y = ((Scalar) arg                       ).value;
            x = ((Scalar) operands[1].eval (context)).value;
        }
        else throw new EvaluationException ("type mismatch");

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