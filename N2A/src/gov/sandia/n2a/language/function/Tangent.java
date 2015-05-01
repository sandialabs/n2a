/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Tangent extends Operator
{
    public Tangent ()
    {
        name          = "tan";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Type eval (Type[] args)
    {
        if (args[0] instanceof Scalar) return new Scalar (Math.tan (((Scalar) args[0]).value));
        if (args[0] instanceof Matrix)
        {
            return ((Matrix) args[0]).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.tan (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }
}
