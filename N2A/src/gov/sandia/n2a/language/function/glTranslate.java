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

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        Matrix to = (MatrixDense) operands[0].eval (context);

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

    public String toString ()
    {
        return "glTranslate";
    }
}
