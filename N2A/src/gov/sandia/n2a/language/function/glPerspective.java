/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class glPerspective extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "glPerspective";
            }

            public Operator createInstance ()
            {
                return new glPerspective ();
            }
        };
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        double fovy   = ((Scalar) operands[0].eval (context)).value;
        double aspect = ((Scalar) operands[1].eval (context)).value;
        double near   = ((Scalar) operands[2].eval (context)).value;
        double far    = ((Scalar) operands[3].eval (context)).value;

        double f = 1 / Math.tan (fovy / 2);

        MatrixDense result = new MatrixDense (4, 4);
        result.set (0, 0, f / aspect);
        result.set (1, 1, f);
        result.set (2, 2, (far + near) / (near - far));
        result.set (3, 2, -1);
        result.set (2, 3, 2 * far * near / (near - far));

        return result;
    }

    public String toString ()
    {
        return "glPerspective";
    }
}
