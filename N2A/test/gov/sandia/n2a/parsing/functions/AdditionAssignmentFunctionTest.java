/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.functions;

import gov.sandia.n2a.parsing.functions.AdditionAssignmentFunction;
import gov.sandia.n2a.parsing.functions.Function;

import org.junit.Test;

public class AdditionAssignmentFunctionTest extends FunctionTest {
    @Override
    protected Function getFunction() {
        return new AdditionAssignmentFunction();
    }

    @Test
    public void testOperator() {
        expectFail(new Object[] {},
            "Invalid function arguments supplied for '+='.  Function not applicable for ().");
        expectFail(new Object[] {0},
            "Invalid function arguments supplied for '+='.  Function not applicable for (Integer).");
        expectOK(0.0, new Object[] {0, 0});
    }
}
