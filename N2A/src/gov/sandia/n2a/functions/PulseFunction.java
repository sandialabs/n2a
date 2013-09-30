/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.functions;

import gov.sandia.n2a.parsing.functions.Function;
import gov.sandia.n2a.parsing.functions.ParameterSet;


public class PulseFunction extends Function
{
    @Override
    public String getName() {
        return "pulse";
    }

    @Override
    public String getDescription() {
        return "pulse input";
    }

    @Override
    public ParameterSet[] getAllowedParameterSets() {
        return new ParameterSet[] {
                new ParameterSet(
                    "!RET", "indepVar", "width", "period", "rise", "fall",
                    Number.class, Number.class, Number.class, Number.class, Number.class, Number.class)
            };
    }

    @Override
    protected Object eval(Object[] args, int parameterSetIndex)
    {
        // TODO - default value for period, rise, and fall should be 0
        double indepVar = ((Number) args[0]).doubleValue();
        double width = ((Number) args[1]).doubleValue();
        double period = ((Number) args[2]).doubleValue();
        double rise = ((Number) args[3]).doubleValue();
        double fall = ((Number) args[4]).doubleValue();
        double result = 0.0;
        if (period != 0) {
            indepVar = indepVar % period;
        }
        if (indepVar>0) {
            if (indepVar<rise) {
                result = indepVar/rise;
            }
            else if (indepVar<width+rise) {
                result = 1.0;
            }
            else if (indepVar<width+rise+fall)  {
                result = 1.0 - (indepVar-width-rise)/fall;
            }
        }
        return result;
    }


}
