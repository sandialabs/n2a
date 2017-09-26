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

    public Type eval (Instance context) throws EvaluationException
    {
        if (operands.length > 1) throw new EvaluationException ("too many arguments to gaussian()");

        Random random;
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) random = new Random ();
        else                   random = simulator.random;

        if (operands.length == 1)
        {
            int dimension = (int) Math.round (((Scalar) operands[0].eval (context)).value);
            if (dimension > 1)
            {
                Matrix result = new MatrixDense (dimension, 1);
                for (int i = 0; i < dimension; i++) result.set (i, random.nextDouble ());
                return result;
            }
        }
        // operands.length == 0
        return new Scalar (random.nextDouble ());
    }

    public String toString ()
    {
        return "uniform";
    }
}
