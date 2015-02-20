/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.gen;

import static org.junit.Assert.assertEquals;
import gov.sandia.n2a.parsing.SpecialVariables;
import gov.sandia.n2a.parsing.functions.EvaluationContext;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ExpressionParser;
import gov.sandia.n2a.parsing.gen.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import javax.measure.unit.SI;

import org.junit.Assert;
import org.junit.Test;

/**
 * This tests the evaluation-related operations on
 * the abstract syntax tree (AST) produced by the
 * expression parser upon a successful parse of an
 * expression.  All expression strings parsed in this
 * test are syntactically valid and produce an AST.
 */

public class ASTNodeBaseEvalTest {

    @Test
    public void testBadLiteralEvaluation() throws ParseException {
        String[] eval = {
            "nm = \"john\" + \"doe\"",
              "Invalid function arguments supplied for '+'.  Function not applicable for (String, String)."
        };

        for(int e = 0; e < eval.length; e += 2) {
            ASTNodeBase eq = ExpressionParser.parse(eval[e]);
            expectFail(eq, eval[e + 1]);
        }
    }

    @Test
    public void testGoodLiteralEvaluation() throws ParseException {
        Object[] eval = {
            "x = 4",      4.0,
            "y = 9.0",    9.0,
            "z = 4-5*3",  -11.0,
            "q = cos(12)", Math.cos(12),
            "x = 5 * 3 - 6 % 4 / 2 + cos(54)",
              14 + Math.cos(54)
        };

        for(int e = 0; e < eval.length; e += 2) {
            ASTNodeBase eq = ExpressionParser.parse((String) eval[e]);
            Object actual = eq.eval();
            assertEquals(eval[e + 1], actual);
        }
    }

    @Test
    public void testSpecialVariables() throws ParseException {
        Object[] eval = {
            "x = " + SpecialVariables.PI, Math.PI,
            "y = " + SpecialVariables.E,  Math.E,
        };

        for(int e = 0; e < eval.length; e += 2) {
            ASTNodeBase eq = ExpressionParser.parse((String) eval[e]);
            Object actual = eq.eval();
            assertEquals(eval[e + 1], actual);
        }
    }

    @Test
    public void testUnits() throws ParseException {
        Object[] eval = {
            "x = 4 + 5 {kg}", 4.0, SI.KILOGRAM,
            "x = 4 + 5 {kg}", 4.0, SI.KILOGRAM,
        };

        /*for(int e = 0; e < eval.length; e += 3) {
            ASTNodeBase eq = ExpressionParser.parse((String) eval[e]);
            EvaluationContext context = new EvaluationContext();
            context.addEquation(eq);
//            System.out.println(eq.toReadableLong());
            Object actual = eq.eval(context);
            assertTrue(actual instanceof Amount);
            Amount<?> amt = (Amount<?>) actual;
            assertEquals(eval[e + 1] instanceof Long, amt.isExact());
            assertTrue(amt.getEstimatedValue() - (Double) eval[e + 1] < 0.1);
            assertEquals(eval[e + 1], actual);
            assertEquals(eval[e + 2], amt.getUnit());
        }*/
    }



    ///////////////////
    // SUPPPLEMENTAL //
    ///////////////////

    protected void expectFail(ASTNodeBase eq, EvaluationContext context, String expectedError) {
        String input = eq.toReadableShort();
        try {
            eq.eval(context);
            Assert.fail("Expected error but did not find one (" + input + ")");
        } catch(Exception ex) {
            String msg = ex.getMessage();
            BufferedReader reader = new BufferedReader(new StringReader(msg));
            if(msg.contains("\n") || msg.contains("\r")) {
                try {
                    msg = reader.readLine();
                } catch(IOException e) {}
            }
            if(!msg.equals(expectedError)) {
                System.out.println("INPUT=" + input);
                System.out.println("ERR=" + msg);
                Assert.fail("Expected error message '" + expectedError + "' but found '" + msg + "' (" + input + ")");
            }
        }
    }

    protected void expectFail(ASTNodeBase eq, String expectedError) {
        String input = eq.toReadableShort();
        try {
            eq.eval();
            Assert.fail("Expected error but did not find one (" + input + ")");
        } catch(Exception ex) {
            String msg = ex.getMessage();
            BufferedReader reader = new BufferedReader(new StringReader(msg));
            if(msg.contains("\n") || msg.contains("\r")) {
                try {
                    msg = reader.readLine();
                } catch(IOException e) {}
            }
            if(!msg.equals(expectedError)) {
                System.out.println("INPUT=" + input);
                System.out.println("ERR=" + msg);
                Assert.fail("Expected error message '" + expectedError + "' but found '" + msg + "' (" + input + ")");
            }
        }
    }
}
