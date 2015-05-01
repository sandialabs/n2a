/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;

public class Modulo extends Operator
{
    public Modulo ()
    {
        name          = "%";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 4;
    }

    public Type eval (Type[] args) throws EvaluationException
    {
        return args[0].modulo (args[1]);
    }
}
