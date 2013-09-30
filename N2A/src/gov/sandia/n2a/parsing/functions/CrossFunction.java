/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.functions;

public class CrossFunction extends Function {
    @Override
    public String getName() {
        return "^^";
    }

    @Override
    public String getDescription() {
        return "cross product";
    }

    @Override
    public ParameterSet[] getAllowedParameterSets() {
        return null;
    }

    @Override
    protected Object eval(Object[] args, int parameterTypeIndex) {
        throw new EvaluationException("Function '" + getName() + "' not implemented.");
    }
}
