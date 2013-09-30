/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.parsing;

import static org.junit.Assert.assertEquals;
import gov.sandia.n2a.parsing.EquationParser;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.gen.ASTFunNode;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTRenderingContext;
import gov.sandia.n2a.parsing.gen.ParseException;

import org.junit.Test;

public class XyceFunctionTranslatorTest {

    @Test
    public void testTranslateUniform() throws ParseException
    {
        String expr = "uniform()";
        String expected = "rand()";
        ParsedEquation peq = EquationParser.parse(expr);
        ASTNodeBase tree = peq.getTree();
        ASTRenderingContext context = new ASTRenderingContext(true);
        context.add (ASTFunNode.class, new XyceFunctionTranslator());
        String newExpr = context.render (tree);
        assertEquals(expected, newExpr);

        expr = "uniform(2,3)";
        expected = "rand()*(3-2) + 2";
        newExpr = context.render (tree);
        assertEquals(expected, newExpr);
    }
}
