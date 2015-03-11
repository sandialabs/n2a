/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.functions;

import gov.sandia.n2a.backend.xyce.params.PulseInputSpecification;
import gov.sandia.n2a.language.Function;

public class XycePulseFunction extends Function
{
    public XycePulseFunction ()
    {
        name          = "pulse";
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
    }

    public Object eval (Object[] args)
    {
        return new PulseInputSpecification(((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue(),
                ((Number)args[2]).doubleValue(), ((Number)args[3]).doubleValue(), ((Number)args[4]).doubleValue(),
                ((Number)args[5]).doubleValue(), ((Number)args[6]).doubleValue());
    }

}
