/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Uniform extends Operator
{
    Random random = new Random ();  // TODO: should there be a single shared random number generator across an entire N2A runtime?

    public Uniform ()
    {
        name          = "uniform";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Type eval (Type[] args) throws EvaluationException
    {
        if (args.length > 1) throw new EvaluationException ("too many arguments to uniform()");
        if (args.length == 1)
        {
            int dimension = (int) Math.round (((Scalar) args[0]).value);
            if (dimension > 1)
            {
                Matrix result = new Matrix (dimension, 1);
                for (int i = 0; i < dimension; i++) result.value[0][i] = random.nextDouble ();
                return result;
            }
        }
        return new Scalar (random.nextDouble ());
    }
}
