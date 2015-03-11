/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;

public class Gauss extends Function
{
    public Gauss ()
    {
        name          = "gaussian";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args) throws EvaluationException
    {
        int dimension = ((Number) args[0]).intValue ();
        if (dimension == 3)
        {
            throw new EvaluationException ("gaussian(3) not yet implemented");
        }
        else
        {
            double r     = Math.sqrt (-2 * Math.log (Math.random ()));
            double theta = 2 * Math.PI * Math.random ();
            return r * Math.cos (theta);
        }
    }

}
