/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;;

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

    public Type eval (Instance context) throws EvaluationException
    {
        Random random;
        Simulator simulator = Simulator.getSimulator (context);
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
