/*
Copyright 2023-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
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

    public void determineExponent (ExponentContext context)
    {
        for (Operator op : operands) op.determineExponent (context);
        updateExponent (context, 0, MSB - 1);  // A pure rotation matrix only needs to represent 1.
        // No relevant keyword arguments.
    }

    public void determineExponentNext ()
    {
        exponentNext = exponent;  // exponent is assigned 0 above

        // Determine average exponent of operands, then force them to agree.
        int avg = 0;
        for (Operator op : operands)
        {
            if (op.getType () instanceof Matrix) avg += op.exponent * 3;
            else                                 avg += op.exponent;
        }
        avg /= 4;
        for (Operator op : operands)
        {
            op.exponentNext = avg;
            op.determineExponentNext ();
        }
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public static MatrixDense make (double a, Matrix xyz)
    {
        double degrees = Math.PI / 180;
        double c = Math.cos (a * degrees);
        double s = Math.sin (a * degrees);
        double c1 = 1 - c;

        xyz = xyz.normalize ();
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

    public Type eval (Instance context) throws EvaluationException
    {
        double a = ((Scalar) operands[0].eval (context)).value;
        Matrix xyz;
        Type op1 = operands[1].eval (context);
        if (op1 instanceof Matrix)
        {
            xyz = (Matrix) op1;
        }
        else
        {
            xyz = new MatrixDense (3, 1);
            double x = 0;
            double y = 0;
            double z = 0;
            if (op1 != null) x = ((Scalar) op1).value;
            if (operands.length > 2) y = ((Scalar) operands[2].eval (context)).value;
            if (operands.length > 3) z = ((Scalar) operands[3].eval (context)).value;
            xyz.set (0, x);
            xyz.set (1, y);
            xyz.set (2, z);
        }
        return make (a, xyz);
    }

    public String toString ()
    {
        return "glRotate";
    }
}
