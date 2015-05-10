/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Uniform extends Function
{
    Random random = new Random ();  // TODO: should there be a single shared random number generator across an entire N2A runtime?

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

    public Type eval (EvaluationContext context) throws EvaluationException
    {
        if (operands.length > 1) throw new EvaluationException ("too many arguments to gaussian()");
        if (operands.length == 1)
        {
            int dimension = (int) Math.round (((Scalar) operands[0].eval (context)).value);
            if (dimension > 1)
            {
                Matrix result = new Matrix (dimension, 1);
                for (int i = 0; i < dimension; i++) result.value[0][i] = random.nextDouble ();
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
