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
    public void testSmallSet() throws ParseException {
        Object[] eval = {
            "x = 4",     4.0,
            "y = 9",     9.0,
            "z = x * y", 36.0,
            "q = z + x", 40.0
        };

        EvaluationContext context = new EvaluationContext();
        ASTNodeBase[] eq = new ASTNodeBase[eval.length];
        for(int e = 0; e < eval.length; e += 2) {
            eq[e] = ExpressionParser.parse((String) eval[e]);
            context.addEquation(eq[e]);
        }

        for(int e = 0; e < eval.length; e += 2) {
            Object actual = eq[e].eval(context);
            assertEquals(eval[e + 1], actual);
        }
    }

    @Test
    public void testSmallSetWithConstants() throws ParseException {
        Object[] eval = {
            "z = x * y", 36.0,
            "q = z + x", 40.0
        };

        EvaluationContext context = new EvaluationContext();
        ASTNodeBase[] eq = new ASTNodeBase[eval.length];
        for(int e = 0; e < eval.length; e += 2) {
            eq[e] = ExpressionParser.parse((String) eval[e]);
            context.addEquation(eq[e]);
        }

        // Just directly set values for constant variables.
        // No creation of an AST is necessary in this case.
        context.setValueForVariable("x", 4L);
        context.setValueForVariable("y", 9L);

        for(int e = 0; e < eval.length; e += 2) {
            Object actual = eq[e].eval(context);
            assertEquals(eval[e + 1], actual);
        }
    }

    @Test
    public void testLargeSet() throws ParseException {
        double x, y, z, gg, hh, t, ff, rr;

        Object[] eval = {
            "x = 4",                          x = 4L,
            "y = 9",                          y = 9L,
            "z = x * y",                      z = 36.0,
            "gg = sin(x)",                    gg = Math.sin(x),
            "hh = 1 / 100",                   hh = 0.01,
            "t = $pi / 4 + z",                t = Math.PI / 4 + z,
            "ff = 45 + 8 / z - tan(3 - 7.0)", ff = 45L + 8L / z - Math.tan(3 - 7.0),
            "rr = 0.33 ^ 3.44 + t + 20",      rr = Math.pow(0.33, 3.44) + t + 20,
            "q = z + x - 5 * (gg / hh + cos(t)) ^ (ff * rr) - y",
              z + x - 5 * Math.pow(gg / hh + Math.cos(t), ff * rr) - y
        };

        EvaluationContext context = new EvaluationContext();
        ASTNodeBase[] eq = new ASTNodeBase[eval.length];
        for(int e = 0; e < eval.length; e += 2) {
            eq[e] = ExpressionParser.parse((String) eval[e]);
            context.addEquation(eq[e]);
        }

        for(int e = 0; e < eval.length; e += 2) {
            Object actual = eq[e].eval(context);
            assertEquals(eval[e + 1], actual);
        }
    }

    @Test
    public void testInfiniteRecursion() throws ParseException {
        String[] eval = {
            "A = B + 5", "Infinite recursion detected while evaluating variable 'B'.",
            "B = A + 5", "Infinite recursion detected while evaluating variable 'A'."
            // No long permit /= in language
            //"x = x / 5", "Infinite recursion detected while evaluating variable 'x'.",
            //"x /= 5",    "Infinite recursion detected while evaluating variable 'x'."
        };

        for(int e = 0; e < eval.length; e += 2) {
            EvaluationContext context = new EvaluationContext();
            ASTNodeBase[] eq = new ASTNodeBase[eval.length / 2];
            for(int f = 0; f < eval.length; f += 2) {
                eq[f / 2] = ExpressionParser.parse(eval[f]);
                context.addEquation(eq[f / 2]);
            }
            expectFail(eq[e / 2], context, eval[e + 1]);
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

    @Test
    public void testMatrices() throws ParseException {
        Object[] eval = {
            "y = 1",         1.0,
            "b = 2",         2.0,
            "c = 3",         3.0,
            "d = [y, b, c]", new Object[][] {{1.0, 2.0, 3.0}}
        };

        EvaluationContext context = new EvaluationContext();
        ASTNodeBase[] eq = new ASTNodeBase[eval.length];
        for(int e = 0; e < eval.length; e += 2) {
            eq[e] = ExpressionParser.parse((String) eval[e]);
            context.addEquation(eq[e]);
        }

        for(int e = 0; e < eval.length; e += 2) {
            Object actual = eq[e].eval(context);
            if(eval[e + 1].getClass().isArray() && actual.getClass().isArray()) {
                Object[][] expectedArr = (Object[][]) eval[e + 1];
                Object[][] actualArr = (Object[][]) actual;
                assertEquals(expectedArr.length, actualArr.length);
                for(int r = 0; r < expectedArr.length; r++) {
                    Object[] eo = expectedArr[r];
                    Object[] ao = actualArr[r];
                    assertEquals(eo.length, ao.length);
                    for(int c = 0; c < eo.length; c++) {
                        assertEquals(eo[c], ao[c]);
                    }
                }
            } else {
                assertEquals(eval[e + 1], actual);
            }
        }
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
