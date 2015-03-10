/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.ParameterSet;

public class AND extends Function {
    @Override
    public String getName() {
        return "&&";
    }

    @Override
    public String getDescription() {
        return "logical and";
    }

    @Override
    public ParameterSet[] getAllowedParameterSets() {
        return new ParameterSet[] {
            new ParameterSet(
                "!RET", "val1", "val2",
                Boolean.class, Boolean.class, Boolean.class)
        };
    }

    @Override
    protected Object eval(Object[] args, int parameterTypeIndex) {
        return (Boolean) args[0] && (Boolean) args[1];
    }
}
