/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;

public class Uniform extends Function
{
    public Uniform ()
    {
        name          = "uniform";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args)
    {
        if (args.length == 1)
        {
            int dimension = ((Number) args[0]).intValue ();
            if (dimension != 1) throw new EvaluationException ("Vector form of uniform distribution not yet implemented.");
            // TODO: Add code to generate vectors with uniform distribution
        }
        return Math.random ();
    }
}
