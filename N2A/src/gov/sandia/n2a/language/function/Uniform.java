/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;

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

    public void determineExponent (Variable from)
    {
        if (operands.length > 0)
        {
            Operator op = operands[0];
            op.determineExponent (from);
            if (op.exponent != UNKNOWN)
            {
                updateExponent (from, op.exponent, op.center);
            }
        }
        else
        {
            updateExponent (from, -1, MSB);  // Current implementation never quite reaches 1, only [0,1).
        }
    }

    public void determineExponentNext (Variable from)
    {
        if (operands.length > 0) super.determineExponentNext (from);
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
            return new Scalar (random.nextDouble () * ((Scalar) sigma).value);
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
            return new Scalar (random.nextDouble ());  // We could throw an exception, but this is easy enough.
        }
    }

    public String toString ()
    {
        return "uniform";
    }
}
