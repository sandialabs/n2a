/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.functions;

public class DivisionFunction extends Function {
    @Override
    public String getName() {
        return "/";
    }

    @Override
    public String getDescription() {
        return "arithmetic division";
    }

    @Override
    public ParameterSet[] getAllowedParameterSets() {
        return new ParameterSet[] {
            new ParameterSet(
                "!RET", "val1", "val2",
                Number.class, Number.class, Number.class)
        };
    }

    @Override
    protected Object eval(Object[] args, int parameterTypeIndex) {
        return ((Number) args[0]).doubleValue() / ((Number) args[1]).doubleValue();
    }
}
