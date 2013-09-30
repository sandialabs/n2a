/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.gen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import gov.sandia.n2a.parsing.EquationParser;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.functions.FunctionList;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTNodeRenderer;
import gov.sandia.n2a.parsing.gen.ASTOpNode;
import gov.sandia.n2a.parsing.gen.ASTRenderingContext;
import gov.sandia.n2a.parsing.gen.ASTVarNode;

import org.junit.Test;

/**
 * This tests the non-evaluation-related operations on
 * the abstract syntax tree (AST) produced by the
 * expression parser upon a successful parse of an
 * expression.  All expression strings parsed in this
 * test are syntactically valid and produce an AST.
 */

public class ASTNodeRendererTest {
    @Test
    public void testNodeRenderer() {
        try {
            String expr = "x = y + 4";
            String expected = "space.x = space.y + 4.0";
            ParsedEquation peq = EquationParser.parse(expr);
            ASTNodeBase tree = peq.getTree();
            final String prefix = "space";
            ASTRenderingContext context = new ASTRenderingContext(true);
            context.add (ASTVarNode.class, new ASTNodeRenderer() {
                public String render(ASTNodeBase node, ASTRenderingContext context) {
                    return prefix + "." + node;
                }
            });
            String newExpr = context.render (tree);
            //ParsedEquation peq2 = EquationParser.parse(newExpr);
            //System.out.println(newExpr);
            assertEquals(expected, newExpr);
        } catch(Exception e) {
            fail("Should not have failed!");
        }
    }

    @Test
    public void testPowerChange() {
        try {
            String expr = "x = a ^ (b + c)";
            ParsedEquation peq = EquationParser.parse(expr);
            ASTNodeBase tree = peq.getTree();
            ASTRenderingContext context = new ASTRenderingContext(true);
            context.add (ASTOpNode.class, new ASTNodeRenderer() {
                public String render(ASTNodeBase node, ASTRenderingContext context) {
                    ASTOpNode op = (ASTOpNode) node;
                    if(op.getFunction() == FunctionList.get(FunctionList.OP_POWER)) {
                        return "pow(" + context.render (op.getChild (0)) + ", " + context.render (op.getChild (1)) + ")";
                    }
                    return node.render (context);
                }
            });
            String newExpr = context.render (tree);
            assertEquals("x = pow(a, b + c)", newExpr);
        } catch(Exception e) {
            fail("Should not have failed!");
        }
    }
}
