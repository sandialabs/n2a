/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.op;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.ParameterSet;

public class Trace extends Function
{
    @Override
    public String getName ()
    {
        return "trace";
    }

    @Override
    public String getDescription ()
    {
        return "outputs values to standard out, then passes them on to the rest of the expression";
    }

    @Override
    public ParameterSet[] getAllowedParameterSets ()
    {
        return new ParameterSet[]
        {
            new ParameterSet ("!RET",       "val1",       "val2",
                              Number.class, Number.class, String.class),
            //new ParameterSet ("!RET",       "val1",
            //                  Number.class, Number.class)
        };
    }

    @Override
    protected Object eval(Object[] args, int parameterSetIndex) 
    {
        if (parameterSetIndex == 0)
        {
            return ((Number) args[0]).doubleValue ();
        }
        throw new EvaluationException ("This form of trace() is not implemented.");
    }

    @Override
    public boolean isOutput ()
    {
        return true;
    }
}
