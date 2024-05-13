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

public class Uniform extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "uniform";
            }

            public Operator createInstance ()
            {
                return new Uniform ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].simplify (from, evalOnly);
        if (operands.length == 1)
        {
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
            updateExponent (context, -1 - MSB, MSB);  // Current implementation never quite reaches 1, only [0,1).
            return;
        }

        for (int i = 0; i < operands.length; i++) operands[i].determineExponent (context);

        if (operands.length == 1)
        {
            Operator op = operands[0];
            if (op.exponent != UNKNOWN) updateExponent (context, op.exponent, op.center);
            return;
        }

        // operands.length >= 2
        Operator op0 = operands[0];  // lo
        Operator op1 = operands[1];  // hi
        int exp  = Math.max (op0.exponent, op1.exponent);
        int cent = (op0.center + op1.center) / 2;
        if (exp != UNKNOWN) updateExponent (context, exp, cent);
    }

    public void determineExponentNext ()
    {
        // We refuse to accommodate scaling in the zero-parameter form.
        // Handle other forms graciously.
        if (operands.length > 0) super.determineExponentNext ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        Random random;
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) random = new Random ();
        else                   random = simulator.random;

        if (operands.length == 0) return new Scalar (random.nextDouble ());

        Type sigma = operands[0].eval (context);
        if (sigma instanceof Scalar)
        {
            double u = random.nextDouble ();
            double lo = ((Scalar) sigma).value;

            // Check if this is actually an interval form.
            if (operands.length > 1)
            {
                double hi = ((Scalar) operands[1].eval (context)).value;
                double step = 1;
                if (operands.length > 2) step = ((Scalar) operands[2].eval (context)).value;
                int steps = (int) Math.floor ((hi - lo) / step) + 1;
                return new Scalar (lo + step * Math.floor (u * steps));
            }

            return new Scalar (u * lo);  // Non-interval form. "lo" is really just "sigma"
        }
        else if (sigma instanceof Matrix)
        {
            Matrix scale = (Matrix) sigma;
            int rows    = scale.rows ();
            int columns = scale.columns ();
            if (columns == 1)
            {
                Matrix result = new MatrixDense (rows, 1);
                for (int i = 0; i < rows; i++) result.set (i, random.nextDouble () * scale.get (i, 0));
                return result;
            }
            else if (rows == 1)
            {
                Matrix result = new MatrixDense (columns, 1);
                for (int i = 0; i < columns; i++) result.set (i, random.nextDouble () * scale.get (0, i));
                return result;
            }
            else
            {
                Matrix temp = new MatrixDense (columns, 1);
                for (int i = 0; i < columns; i++) temp.set (i, random.nextDouble ());
                return sigma.multiply (temp);
            }
        }
        else
        {
            return new Scalar (random.nextDouble ());  // We could throw an exception, but this is an adequate fallback.
        }
    }

    public String toString ()
    {
        return "uniform";
    }
}
