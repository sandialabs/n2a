/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class Gaussian extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "gaussian";
            }

            public Operator createInstance ()
            {
                return new Gaussian ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        if (operands.length > 0)
        {
            operands[0] = operands[0].simplify (from, evalOnly);
            Operator sigma = operands[0];
            if (sigma.isScalar ()  &&  sigma.getDouble () == 0)
            {
                from.changed = true;
                sigma.parent = parent;
                return sigma;
            }
        }
        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        if (operands.length == 0)
        {
            // The largest Gaussian PRNG output is determined by the bit precision of the underlying
            // uniform PRNG output. The Box-Muller implementation in the C Backend runtime will not
            // exceed 6.66 standard deviations when using 32-bit uniform numbers. Thus, 7 std is safe.
            // log2(7)~=2.81, so magnitude of msb is 2
            // Since about 68% of all results are less than 1 sigma, center can point to bit holding 2^-1.
            updateExponent (context, 2 - MSB, MSB - 3);
        }
        else
        {
            Operator op = operands[0];
            op.determineExponent (context);
            if (op.exponent != UNKNOWN) updateExponent (context, op.exponent + 2, op.center - 2);  // Per argument above, we need to allow up to 7x the given sigma.
        }
    }

    public void determineExponentNext ()
    {
        if (operands.length > 0) super.determineExponentNext ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        Random random;
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) random = new Random ();
        else                   random = simulator.random;

        if (operands.length == 0) return new Scalar (random.nextGaussian ());

        Type sigma = operands[0].eval (context);
        if (sigma instanceof Scalar)
        {
            return new Scalar (random.nextGaussian () * ((Scalar) sigma).value);
        }
        else if (sigma instanceof Matrix)
        {
            Matrix scale = (Matrix) sigma;
            int rows    = scale.rows ();
            int columns = scale.columns ();
            if (columns == 1)
            {
                Matrix result = new MatrixDense (rows, 1);
                for (int i = 0; i < rows; i++) result.set (i, random.nextGaussian () * scale.get (i, 0));
                return result;
            }
            else if (rows == 1)
            {
                Matrix result = new MatrixDense (columns, 1);
                for (int i = 0; i < columns; i++) result.set (i, random.nextGaussian () * scale.get (0, i));
                return result;
            }
            else
            {
                Matrix temp = new MatrixDense (columns, 1);
                for (int i = 0; i < columns; i++) temp.set (i, random.nextGaussian ());
                return sigma.multiply (temp);
            }
        }
        else
        {
            return new Scalar (random.nextGaussian ());  // We could throw an exception, but this is easy enough.
        }
    }

    public String toString ()
    {
        return "gaussian";
    }
}
