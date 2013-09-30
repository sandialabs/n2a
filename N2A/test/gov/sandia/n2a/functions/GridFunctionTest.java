/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.functions;

import gov.sandia.n2a.functions.GridFunction;
import gov.sandia.n2a.parsing.functions.Function;
import gov.sandia.n2a.parsing.functions.FunctionTest;

import org.junit.Test;

public class GridFunctionTest extends FunctionTest {
    @Override
    protected Function getFunction() {
        return new GridFunction();
    }

    @Test
    public void testOperator() {
        expectFail(new Object[] {},
            "Invalid function arguments supplied for 'grid'.  Function not applicable for ().");
        expectFail(new Object[] {0},
            "Invalid function arguments supplied for 'grid'.  Function not applicable for (Integer).");
        expectOKArray(new Double[] {0.5,0.5}, new Object[] {1, 1, 1, 1, 1, 1, 0});
    }

}
