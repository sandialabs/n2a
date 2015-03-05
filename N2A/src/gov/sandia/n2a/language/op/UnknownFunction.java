/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

public class UnknownFunction extends Function {

    private String name;
    public UnknownFunction(String nm) {
        name = nm;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public ParameterSet[] getAllowedParameterSets() {
        return null;
    }

    @Override
    protected Object eval(Object[] args, int parameterSetIndex) {
        throw new EvaluationException("Function '" + getName() + "' not implemented.");
    }
}
