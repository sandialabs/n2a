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
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class glFrustum extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "glFrustum";
            }

            public Operator createInstance ()
            {
                return new glFrustum ();
            }
        };
    }

    /**
        Generic routine to determine exponent for gl projection functions.
        The resulting matrix must encompass both values at floating-point magnitude 1 (rotation)
        and values at about the scale of spatial coordinates (translation, scaling).
        Our strategy is to set the exponent near the scale of coordinates, while constraining
        them to minimally accommodate rotation.
    **/
    public static void determineExponent (Function f, ExponentContext context)
    {
        int cent  = 0;
        int pow   = 0;
        int count = 0;
        for (Operator op : f.operands)
        {
            op.determineExponent (context);
            if (op.exponent != UNKNOWN)
            {
                cent += op.center;
                pow  += op.exponent;
                count++;
            }
        }
        if (count > 0)
        {
            cent /= count;
            pow  /= count;

            // Impose bounds on exponent. This will potentially make the resulting matrix lose significance.
            // There's nothing we can do about that. Fixed-point has limited capacity to represent a projection matrix.
            int shift = 0;
            if      (pow < 0      ) shift = pow;            // Must be able to represent 1 for rotation matrix.
            else if (pow > MSB - 8) shift = pow - MSB + 8;  // Must have at least 8 bits below decimal to have a crude rotation matrix.
            cent += shift;
            pow  -= shift;

            f.updateExponent (context, pow, cent);
        }

        // No relevant keyword arguments.
    }

    /**
        Generic routine for finalizing exponent for gl projection functions.
        Sets exponentNext to be the same as exponent.
        Assumes our exponent is currently set to the average of our operands.
        Imposes this average on all the operands so we can pass a single exponent parameter in generated code.
    **/
    public static void determineExponentNext (Function f)
    {
        f.exponentNext = f.exponent;
        for (Operator op : f.operands)
        {
            op.exponentNext = f.exponent;
            op.determineExponentNext ();
        }
    }

    public void determineExponent (ExponentContext context)
    {
        determineExponent (this, context);
    }

    public void determineExponentNext ()
    {
        determineExponentNext (this);
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        double l = ((Scalar) operands[0].eval (context)).value;
        double r = ((Scalar) operands[1].eval (context)).value;
        double b = ((Scalar) operands[2].eval (context)).value;
        double t = ((Scalar) operands[3].eval (context)).value;
        double n = ((Scalar) operands[4].eval (context)).value;
        double f = ((Scalar) operands[5].eval (context)).value;

        MatrixDense result = new MatrixDense (4, 4);
        result.set (0, 0,  2 * n / (r - l));
        result.set (1, 1,  2 * n / (t - b));
        result.set (0, 2,  (r + l) / (r - l));
        result.set (1, 2,  (t + b) / (t - b));
        result.set (2, 2, -(f + n) / (f - n));
        result.set (3, 2, -1);
        result.set (2, 3, -2 * f * n / (f - n));

        return result;
    }

    public String toString ()
    {
        return "glFrustum";
    }
}
