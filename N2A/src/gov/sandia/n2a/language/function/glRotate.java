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

public class glRotate extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "glRotate";
            }

            public Operator createInstance ()
            {
                return new glRotate ();
            }
        };
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        double      a   = ((Scalar)     operands[0].eval (context)).value;
        MatrixDense xyz = (MatrixDense) operands[1].eval (context);

        double c = Math.cos (a);
        double s = Math.sin (a);
        double c1 = 1 - c;

        xyz = xyz.divide (xyz.norm (2));
        double x = xyz.get (0);
        double y = xyz.get (1);
        double z = xyz.get (2);

        MatrixDense result = new MatrixDense (4, 4);
        result.set (0, 0, x*x*c1+c);
        result.set (1, 0, y*x*c1+z*s);
        result.set (2, 0, x*z*c1-y*s);
        result.set (0, 1, x*y*c1-z*s);
        result.set (1, 1, y*y*c1+c);
        result.set (2, 1, y*z*c1+x*s);
        result.set (0, 2, x*z*c1+y*s);
        result.set (1, 2, y*z*c1-x*s);
        result.set (2, 2, z*z*c1+c);
        result.set (3, 3, 1);

        return result;
    }

    public String toString ()
    {
        return "glRotate";
    }
}
