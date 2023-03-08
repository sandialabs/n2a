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

public class glLookAt extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "glLookAt";
            }

            public Operator createInstance ()
            {
                return new glLookAt ();
            }
        };
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        MatrixDense eye = (MatrixDense) operands[0].eval (context);
        MatrixDense center;
        MatrixDense up;

        if (operands.length > 1) center = (MatrixDense) operands[1].eval (context);
        else                     center = new MatrixDense (3, 1);  // cleared to 0, so default center is origin

        if (operands.length > 2) up = (MatrixDense) operands[2].eval (context);
        else
        {
            up = new MatrixDense (3, 1);  // cleared to 0
            up.set (1, 1);                // default up is y
        }

        // Create an orthonormal frame
        MatrixDense f = center.subtract (eye);
        f = f.divide (f.norm (2));
        MatrixDense u = up.divide (up.norm (2));
        MatrixDense s = f.cross (u);
        s = s.divide (s.norm (2));
        u = s.cross (f);

        MatrixDense R = new MatrixDense (4, 4);
        R.set (0, 0,  s.get (0));
        R.set (0, 1,  s.get (1));
        R.set (0, 2,  s.get (2));
        R.set (1, 0,  u.get (0));
        R.set (1, 1,  u.get (1));
        R.set (1, 2,  u.get (2));
        R.set (2, 0, -f.get (0));
        R.set (2, 1, -f.get (1));
        R.set (2, 2, -f.get (2));
        R.set (3, 3, 1);

        MatrixDense T = new MatrixDense (4, 4);
        T.set (0, 3, -eye.get (0));
        T.set (1, 3, -eye.get (1));
        T.set (2, 3, -eye.get (2));
        T.set (0, 0, 1);
        T.set (1, 1, 1);
        T.set (2, 2, 1);
        T.set (3, 3, 1);

        return R.multiply (T);
    }

    public String toString ()
    {
        return "glLookAt";
    }
}
