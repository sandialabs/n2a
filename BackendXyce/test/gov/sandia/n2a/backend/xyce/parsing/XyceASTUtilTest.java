/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.parsing;

import static org.junit.Assert.*;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ExpressionParser;
import gov.sandia.n2a.parsing.gen.ParseException;

import org.junit.Test;

public class XyceASTUtilTest {

    @Test
    public void testHasUnknownFunction() throws ParseException {
        ASTNodeBase eqTree;
        eqTree = ExpressionParser.parse ("a=2+3");
        assertEquals(false, XyceASTUtil.hasUnknownFunction(eqTree));
        eqTree = ExpressionParser.parse("b=cos(x)");
        assertEquals(false, XyceASTUtil.hasUnknownFunction(eqTree));
        eqTree = ExpressionParser.parse("c=bogus(y)");
    }

}
