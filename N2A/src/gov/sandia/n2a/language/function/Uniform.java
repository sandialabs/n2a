/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;

public class Uniform extends Function
{
    Random random = new Random ();  // TODO: should there be a single shared random number generator across an entire N2A runtime?

    public Uniform ()
    {
        name          = "uniform";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args) throws EvaluationException
    {
        if (args.length > 1) throw new EvaluationException ("too many arguments to uniform()");
        if (args.length == 1)
        {
            int dimension = ((Number) args[0]).intValue ();
            if (dimension > 1)
            {
                Number[] result = new Number[dimension];
                for (int i = 0; i < dimension; i++) result[i] = random.nextDouble ();
                return result;
            }
        }
        return random.nextDouble ();
    }
}
