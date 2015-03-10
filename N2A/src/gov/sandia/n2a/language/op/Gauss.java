/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.ParameterSet;

public class Gauss extends Function {

    @Override
    public String getName() {
        return "gaussian";
    }

    @Override
    public String getDescription() {
        return "gaussian distribution";
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
    protected Object eval (Object[] args, int parameterSetIndex)
    {
        double location = ((Number) args[0]).doubleValue ();
        double scale    = ((Number) args[1]).doubleValue ();
        double r = Math.sqrt (-2 * Math.log (Math.random ()));
        double theta = 2 * Math.PI * Math.random ();
        return location + (scale * r * Math.cos(theta));
    }

}
