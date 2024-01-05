/*
Copyright 2023-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class glTranslate extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "glTranslate";
            }

            public Operator createInstance ()
            {
                return new glTranslate ();
            }
        };
    }

    // Function.determineExponent() is sufficient.

    public void determineExponentNext ()
    {
        glFrustum.determineExponentNext (this);
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public static MatrixDense make (Matrix to)
    {
        MatrixDense result = new MatrixDense (4, 4);
        result.set (0, 3, to.get (0));
        result.set (1, 3, to.get (1));
        result.set (2, 3, to.get (2));
        result.set (0, 0, 1);
        result.set (1, 1, 1);
        result.set (2, 2, 1);
        result.set (3, 3, 1);
        return result;
    }

    public Type eval (Instance context) throws EvaluationException
    {
        Matrix to;
        Type op0 = operands[0].eval (context);
        if (op0 instanceof Matrix)
        {
            to = (Matrix) op0;
        }
        else
        {
            to = new MatrixDense (3, 1);
            double x = 0;
            double y = 0;
            double z = 0;
            if (op0 != null) x = ((Scalar) op0).value;
            if (operands.length > 1) y = ((Scalar) operands[1].eval (context)).value;
            if (operands.length > 2) z = ((Scalar) operands[2].eval (context)).value;
            to.set (0, x);
            to.set (1, y);
            to.set (2, z);
        }
        return make (to);
    }

    public String toString ()
    {
        return "glTranslate";
    }
}
