/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Norm extends Function
{
    public Norm ()
    {
        name          = "norm";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Type eval (Type[] args)
    {
        double n = -1;
        Matrix A = null;
        if (args[0] instanceof Scalar) n = ((Scalar) args[0]).value;
        if (args[1] instanceof Matrix) A = (Matrix) args[1];
        if (n < 0  ||  A == null) throw new EvaluationException ("type mismatch");
        return new Scalar (A.norm (n));
    }
}
