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

public class Trace extends Operator
{
    public Trace ()
    {
        name          = "trace";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
        output        = true;
    }

    public boolean isOutput ()
    {
        return true;
    }

    public Type eval (Type[] args) 
    {
        if (args.length != 2) throw new EvaluationException ("This form of trace() is not implemented.");
        return args[0];
    }
}
