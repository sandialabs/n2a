/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.ParameterSet;

public class Uniform extends Function {

    @Override
    public String getName() {
        return "uniform";
    }

    @Override
    public String getDescription() {
        return "uniform distribution";
    }

    @Override
    public ParameterSet[] getAllowedParameterSets() {
        return new ParameterSet[] {
                new ParameterSet(
                    "!RET", "val1", "val2",
                    Number.class, Number.class, Number.class),
                new ParameterSet(
                        "!RET", "val1",
                        Number.class, Number.class),
                new ParameterSet(
                        "!RET",
                        Number.class),
                    
            };
    }

    @Override
    protected Object eval(Object[] args, int parameterSetIndex) 
    {
        if (parameterSetIndex == 0)
        {
            double a = ((Number) args[0]).doubleValue ();
            double b = ((Number) args[1]).doubleValue ();
            return a + (b - a) * Math.random ();
        }
        else if (parameterSetIndex == 1)
        {
            double b = ((Number) args[0]).doubleValue ();
            return Math.random () * b;
        }
        // parameterSetIndex == 2, or anything else not in [0,1]
        return Math.random ();
    }
}
