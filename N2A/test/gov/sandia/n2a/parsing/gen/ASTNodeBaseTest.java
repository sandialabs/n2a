/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.gen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import gov.sandia.n2a.parsing.EquationParser;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.functions.ExponentiationFunction;
import gov.sandia.n2a.parsing.functions.FunctionList;
import gov.sandia.n2a.parsing.functions.ListSubscriptExpression;
import gov.sandia.n2a.parsing.gen.ASTConstant;
import gov.sandia.n2a.parsing.gen.ASTFunNode;
import gov.sandia.n2a.parsing.gen.ASTListNode;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTNodeRenderer;
import gov.sandia.n2a.parsing.gen.ASTNodeTransformer;
import gov.sandia.n2a.parsing.gen.ASTOpNode;
import gov.sandia.n2a.parsing.gen.ASTRenderingContext;
import gov.sandia.n2a.parsing.gen.ASTTransformationContext;
import gov.sandia.n2a.parsing.gen.ASTVarNode;
import gov.sandia.n2a.parsing.gen.ExpressionParser;
import gov.sandia.n2a.parsing.gen.ParseException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * This tests the non-evaluation-related operations on
 * the abstract syntax tree (AST) produced by the
 * expression parser upon a successful parse of an
 * expression.  All expression strings parsed in this
 * test are syntactically valid and produce an AST.
 */

public class ASTNodeBaseTest {

    private String[] testExprs;

    // TODO: Does not yet test Long / Integer data types.

    @Before
    public void setup() {
        testExprs = new String[] {
            "+2.0",
            "x",
            " \t y = m* x +   (( b )) ",
            "2.0 + 2.0",
            "(2.0 % t.y) * (  (  u$$)) && !p__.$f",
            "cos(y)",
            "V' += ( m + a') - 35.0 + tanh(v$)",
            //"foo(x) = f ^ 2.0 - (10.3e20 ^ tan(3.0) + u.t) * 22.0 - o[3.0, r]",
            "u__4=2.0-cos(h+=   5.0, tanh(rr) *t[r] / \"earth\")*t$.f0    ^ ((6.0+!k))",
            //"x[3.0+a+cos(derek(1.0)),9.0]=b=c=0.0",
            "a=\"dog\" ^ derek(a=2.0, 3e3, cos(x-3.0))",
            "x=3.0*x^(2.0+3.0)-t^cos(theta')",
            "yy = !false + 4.0 - (true == ++false)",
            "alpha_m = (25 - V) / (10 * (exp ((25 - V) / 10) - 1))",

            // Precedence
            "x = 3 - 4 / 6 + 7",
            "x = (3 - 4) / 6 + 7",
            "x = 3 - 4 / (6 + 7)",
            "x = (3 - 4) / (6 + 7)",
            "x = 3 * 4 / 6 % 7",
            "x = (3 * 4) / 6 % 7",
            "x = 3 * 4 / (6 % 7)",
            "x = (3 * 4) / (6 % 7)",
        };
    }

    @Test
    public void testSource() throws ParseException {
        for(int e = 0; e < testExprs.length; e++) {
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(testExprs[e], root.getSource());
            for(int c = 0; c < root.getCount(); c++) {
                assertNull(root.getChild(c).getSource());
            }
        }
    }

    @Test
    public void testIndexedSymbol() throws ParseException {
        String test = "Layer.var[7]";
        ASTNodeBase root = ExpressionParser.parse(test);
        checkOpNode(root, "[]", ListSubscriptExpression.class,
            FunctionList.OP_ELEMENT, "Layer.var[7]", "Layer.var[7]", 2);
        ASTNodeBase left = root.getChild(0);
        checkVarNode(left, "Layer.var", 0, "Layer.var");
        ASTNodeBase right = root.getChild(1);
        checkListNode(right, 1, "7");
        ASTNodeBase index = right.getChild(0);
        checkConNode(index, "7", Long.class, 7L, "7");

        // Example of how to get these parts using a ParsedEquation object:
        // ParsedEquation pe = EquationParser.parse(test);
        // String sym = ((ASTVarNode) pe.getTree().getChild(0)).getVariableNameWithOrder();
        // Number index = (Number) (pe.getTree().getChild(1).getChild(0)).getValue();
    }

    @Test
    public void testAssignmentVariableName() throws ParseException {
        String[] syms = {
            null,
            null,
            "y",
            null,
            null,
            null,
            "V",
            //null,
            "u__4",
            //null,
            "a",
            "x",
            "yy",
            "alpha_m",
            "x", "x", "x", "x", "x", "x", "x", "x" // Precedence
        };

        for(int e = 0; e < testExprs.length; e++) {
            String expected = syms[e];
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(expected, root.getVariableName(true, false, true, false));
        }

        // V' += ...
        ASTNodeBase root = ExpressionParser.parse(testExprs[6]);
        assertEquals("V'", root.getVariableName(true, true, true, true));
        assertEquals("V", root.getVariableName(true, true, true, false));
        assertEquals(null, root.getVariableName(true, true, false, true));
        assertEquals(null, root.getVariableName(true, true, false, false));
        assertEquals("V'", root.getVariableName(true, false, true, true));
        assertEquals("V", root.getVariableName(true, false, true, false));
        assertEquals(null, root.getVariableName(true, false, false, true));
        assertEquals(null, root.getVariableName(true, false, false, false));

        assertEquals(null, root.getVariableName(false, true, true, true));
        assertEquals(null, root.getVariableName(false, true, true, false));
        assertEquals(null, root.getVariableName(false, true, false, true));
        assertEquals(null, root.getVariableName(false, true, false, false));
        assertEquals(null, root.getVariableName(false, false, true, true));
        assertEquals(null, root.getVariableName(false, false, true, false));
        assertEquals(null, root.getVariableName(false, false, false, true));
        assertEquals(null, root.getVariableName(false, false, false, false));

        // x
        root = ExpressionParser.parse(testExprs[1]);
        assertEquals("x", root.getVariableName(true, true, true, true));
        assertEquals("x", root.getVariableName(true, true, true, false));
        assertEquals("x", root.getVariableName(true, true, false, true));
        assertEquals("x", root.getVariableName(true, true, false, false));
        assertEquals(null, root.getVariableName(true, false, true, true));
        assertEquals(null, root.getVariableName(true, false, true, false));
        assertEquals(null, root.getVariableName(true, false, false, true));
        assertEquals(null, root.getVariableName(true, false, false, false));

        assertEquals("x", root.getVariableName(false, true, true, true));
        assertEquals("x", root.getVariableName(false, true, true, false));
        assertEquals("x", root.getVariableName(false, true, false, true));
        assertEquals("x", root.getVariableName(false, true, false, false));
        assertEquals(null, root.getVariableName(false, false, true, true));
        assertEquals(null, root.getVariableName(false, false, true, false));
        assertEquals(null, root.getVariableName(false, false, false, true));
        assertEquals(null, root.getVariableName(false, false, false, false));
    }

    @Test
    public void testSymbols() throws ParseException {
        String[] syms = {
            "",
            "x",
            "y|m|x|b",
            "",
            "t.y|u$$|p__.$f",
            "cos|y",
            "V|m|a|tanh|v$",
            //"foo|x|f|u.t|o|r|tan",
            "u__4|cos|h|tanh|rr|t|r|t$.f0|k",
            //"x|a|cos|derek|b|c",
            "derek|a|cos|x",
            "x|t|cos|theta",
            "yy",
            "alpha_m|V|exp",
            "x", "x", "x", "x", "x", "x", "x", "x" // Precedence
        };

        for(int e = 0; e < testExprs.length; e++) {
            Set<String> expected;
            if(syms[e].equals("")) {
                expected = toSet(new String[0]);
            } else {
                String[] expectedArray = syms[e].split("\\|");
                expected = toSet(expectedArray);
            }
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(expected, root.getSymbols());

            // Check a specific child node's symbols.
            if(e == 6) {
                expected = toSet(new String[] {"a", "m"});
                assertEquals(expected, root.getChild(1).getChild(0).getSymbols());
            }
        }
    }

    @Test
    public void testIsSimpleAssignment() throws ParseException {
        boolean[] isSimple = {
            false,
            false,
            true,
            false,
            false,
            false,
            false,
            //false,  // TODO: Consistency with isAssignment!
            true,
            //false,  // TODO: Consistency with isAssignment!
            true,
            true,
            true,
            true,
            true, true, true, true, true, true, true, true
        };

        for(int e = 0; e < testExprs.length; e++) {
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(isSimple[e], root.isSimpleAssignment());
        }
    }

    @Test
    public void testIsAssignment() throws ParseException {
        boolean[] isSimple = {
            false,
            false,
            true,
            false,
            false,
            false,
            true,
            //true,  // TODO: Consistency with isSimpleAssignment!
            true,
            //true,  // TODO: Consistency with isSimpleAssignment!
            true,
            true,
            true,
            true,
            true, true, true, true, true, true, true, true
        };

        for(int e = 0; e < testExprs.length; e++) {
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(isSimple[e], root.isAssignment());
        }
    }

    // TODO: Not implemented
    @Test
    public void testIsSingleSymbol() throws ParseException {
        /*boolean[] isSimple = {
            false,
            false,
            true,
            false,
            false,
            false,
            true,
            true,  // TODO: Consistency with isSimpleAssignment!
            true,
            true,  // TODO: Consistency with isSimpleAssignment!
            true,
            true,
            true,
            true,
            true, true, true, true, true, true, true, true
        };

        for(int e = 0; e < testExprs.length; e++) {
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(isSimple[e], root.isAssignment());
        }*/
    }

    // TODO: Not implemented
    @Test
    public void testGetVarNode() throws ParseException {
        /*boolean[] isSimple = {
            false,
            false,
            true,
            false,
            false,
            false,
            true,
            true,  // TODO: Consistency with isSimpleAssignment!
            true,
            true,  // TODO: Consistency with isSimpleAssignment!
            true,
            true,
            true,
            true,
            true, true, true, true, true, true, true, true
        };

        for(int e = 0; e < testExprs.length; e++) {
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(isSimple[e], root.isAssignment());
        }*/
    }
    @Test
    public void testVariables() throws ParseException {
        String[] syms = {
            "",
            "x",
            "y|m|x|b",
            "",
            "t.y|u$$|p__.$f",
            "y",
            "V|m|a|v$",
            //"x|f|u.t|o|r",
            "u__4|h|rr|t|r|t$.f0|k",
            //"x|a|b|c",
            "a|x",
            "x|t|theta",
            "yy",
            "alpha_m|V",
            "x", "x", "x", "x", "x", "x", "x", "x" // Precedence
        };

        for(int e = 0; e < testExprs.length; e++) {
            Set<String> expected;
            if(syms[e].equals("")) {
                expected = toSet(new String[0]);
            } else {
                String[] expectedArray = syms[e].split("\\|");
                expected = toSet(expectedArray);
            }
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            assertEquals(expected, root.getVariables());

            // Check a specific child node's symbols.
            if(e == 6) {
                expected = toSet(new String[] {"a", "m"});
                assertEquals(expected, root.getChild(1).getChild(0).getVariables());
            }
        }
    }

    private Set<String> toSet(String[] args) {
        return new HashSet<String>(Arrays.asList(args));
    }

    @Test
    public void testReadableLong() throws ParseException {
        String[] rdLongs = {
            "+2.0",
            "x",
            "y = ((m * x) + b)",
            "2.0 + 2.0",
            "((2.0 % t.y) * u$$) && !p__.$f",
            "cos(y)",
            "V' += (((m + a') - 35.0) + tanh(v$))",
            //"foo(x) = (((f ^ 2.0) - (((1.03E21 ^ tan(3.0)) + u.t) * 22.0)) - o[3.0, r])",
            "u__4 = (2.0 - (cos((h += 5.0), ((tanh(rr) * t[r]) / \"earth\")) * (t$.f0 ^ (6.0 + !k))))",
            //"x[((3.0 + a) + cos(derek(1.0))), 9.0] = (b = (c = 0.0))",
            "a = (\"dog\" ^ derek((a = 2.0), 3000.0, cos((x - 3.0))))",
            "x = ((3.0 * (x ^ (2.0 + 3.0))) - (t ^ cos(theta')))",
            "yy = ((!false + 4.0) - (true == ++false))",
            "alpha_m = ((25 - V) / (10 * (exp(((25 - V) / 10)) - 1)))",

            // Precedence
            "x = ((3 - (4 / 6)) + 7)",
            "x = (((3 - 4) / 6) + 7)",
            "x = (3 - (4 / (6 + 7)))",
            "x = ((3 - 4) / (6 + 7))",
            "x = (((3 * 4) / 6) % 7)",
            "x = (((3 * 4) / 6) % 7)",
            "x = ((3 * 4) / (6 % 7))",
            "x = ((3 * 4) / (6 % 7))",
        };

        for(int e = 0; e < testExprs.length; e++) {
            String expected = rdLongs[e];
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            String actual = root.toReadableLong();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadableShort() throws ParseException {
        String[] rdShorts = {
            "2.0",
            "x",
            "y = m * x + b",
            "2.0 + 2.0",
            "2.0 % t.y * u$$ && !p__.$f",
            "cos(y)",
            "V' += m + a' - 35.0 + tanh(v$)",
            //"foo(x) = f ^ 2.0 - (1.03E21 ^ tan(3.0) + u.t) * 22.0 - o[3.0, r]",
            "u__4 = 2.0 - cos(h += 5.0, tanh(rr) * t[r] / \"earth\") * t$.f0 ^ (6.0 + !k)",
            //"x[3.0 + a + cos(derek(1.0)), 9.0] = b = c = 0.0",
            "a = \"dog\" ^ derek(a = 2.0, 3000.0, cos(x - 3.0))",
            "x = 3.0 * x ^ (2.0 + 3.0) - t ^ cos(theta')",
            "yy = !false + 4.0 - (true == false)",
            "alpha_m = (25.0 - V) / (10.0 * (exp((25.0 - V) / 10.0) - 1.0))",

            // Precedence
            "x = 3 - 4 / 6 + 7",
            "x = (3 - 4) / 6 + 7",
            "x = 3 - 4 / (6 + 7)",
            "x = (3 - 4) / (6 + 7)",
            "x = 3 * 4 / 6 % 7",
            "x = 3 * 4 / 6 % 7",
            "x = 3 * 4 / (6 % 7)",
            "x = 3 * 4 / (6 % 7)",
        };

        for(int e = 0; e < testExprs.length; e++) {
            String expected = rdShorts[e];
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            String actual = root.toReadableShort();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadableShortCustom() throws ParseException {
        String[] rdShorts = {
            "2.0",
            "x#",
            "y# = m# * x# + b#",
            "2.0 + 2.0",
            "2.0 % t.y# * u$$# && !p__.$f#",
            "cos@|y#|",
            "V'# += m# + a'# - 35.0 + tanh@|v$#|",
            //"foo@|x#| = f# ^ 2.0 - (1.03E21 ^ tan@|3.0| + u.t#) * 22.0 - o#[3.0, r#]",
            "u__4# = 2.0 - cos@|h# += 5.0, tanh@|rr#| * t#[r#] / \"earth\"| * t$.f0# ^ (6.0 + !k#)",
            //"x#[3.0 + a# + cos@|derek@|1.0||, 9.0] = b# = c# = 0.0",
            "a# = \"dog\" ^ derek@|a# = 2.0, 3000.0, cos@|x# - 3.0||",
            "x# = 3.0 * x# ^ (2.0 + 3.0) - t# ^ cos@|theta'#|",
            "yy# = !false + 4.0 - (true == false)",
            "alpha_m# = (25 - V#) / (10 * (exp@|(25 - V#) / 10| - 1))",

            // Precedence
            "x# = 3 - 4 / 6 + 7",
            "x# = (3 - 4) / 6 + 7",
            "x# = 3 - 4 / (6 + 7)",
            "x# = (3 - 4) / (6 + 7)",
            "x# = 3 * 4 / 6 % 7",
            "x# = 3 * 4 / 6 % 7",
            "x# = 3 * 4 / (6 % 7)",
            "x# = 3 * 4 / (6 % 7)",
        };

        ASTRenderingContext context = new ASTRenderingContext (true);

        // Change rendering of variables to contain a trailing #.
        context.add (ASTVarNode.class, new ASTNodeRenderer() {
            public String render(ASTNodeBase node, ASTRenderingContext context) {
                ASTVarNode varNode = (ASTVarNode) node;
                return varNode.toReadableShort() + "#";
            }
        });

        // Change rendering of function calls to contain a trailing @
        // and have their arguments bounded by | instead of ( and ).
        context.add (ASTFunNode.class, new ASTNodeRenderer() {
            public String render(ASTNodeBase node, ASTRenderingContext context) {
                String ret = node.getValue() + "@|";
                for(int a = 0; a < node.getCount(); a++) {
                    ret += context.render (node.getChild (a));
                    if(a != node.getCount() - 1) {
                        ret += ", ";
                    }
                }
                return ret + "|";
            }
        });

        for(int e = 0; e < testExprs.length; e++) {
            String expected = rdShorts[e];
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            String actual = context.render (root);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadableCustomPrefix() throws ParseException {
        String[] custom = {
            "+(2.0)",
            "x",
            "=(y, +(*(m, x), b))",
            "+(2.0, 2.0)",
            "&&(*(%(2.0, t.y), u$$), !(p__.$f))",
            "cos(y)",
            "+=(V', +(-(+(m, a'), 35.0), tanh(v$)))",
            //"=(foo(x), -(-(^(f, 2.0), *(+(^(1.03E21, tan(3.0)), u.t), 22.0)), [](o, 3.0, r)))",
            "=(u__4, -(2.0, *(cos(+=(h, 5.0), /(*(tanh(rr), [](t, r)), \"earth\")), ^(t$.f0, +(6.0, !(k))))))",
            //"=([](x, +(+(3.0, a), cos(derek(1.0))), 9.0), =(b, =(c, 0.0)))",
            "=(a, ^(\"dog\", derek(=(a, 2.0), 3000.0, cos(-(x, 3.0)))))",
            "=(x, -(*(3.0, ^(x, +(2.0, 3.0))), ^(t, cos(theta'))))",
            "=(yy, -(+(!(false), 4.0), ==(true, +(+(false)))))",
            "=(alpha_m, /(-(25, V), *(10, -(exp(/(-(25, V), 10)), 1))))",

            // Precedence
            "=(x, +(-(3, /(4, 6)), 7))",
            "=(x, +(/(-(3, 4), 6), 7))",
            "=(x, -(3, /(4, +(6, 7))))",
            "=(x, /(-(3, 4), +(6, 7)))",
            "=(x, %(/(*(3, 4), 6), 7))",
            "=(x, %(/(*(3, 4), 6), 7))",
            "=(x, /(*(3, 4), %(6, 7)))",
            "=(x, /(*(3, 4), %(6, 7)))",
        };

        ASTRenderingContext context = new ASTRenderingContext (true);

        // Change rendering of operators to render like functions (prefix notation).
        context.add (ASTOpNode.class, new ASTNodeRenderer() {
            public String render(ASTNodeBase node, ASTRenderingContext context) {
                String ret = node.getValue() + "(";
                for(int a = 0; a < node.getCount(); a++) {
                    ret += context.render (node.getChild (a));
                    if(a != node.getCount() - 1) {
                        ret += ", ";
                    }
                }
                return ret + ")";
            }
        });

        for(int e = 0; e < testExprs.length; e++) {
            String expected = custom[e];
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            String actual = context.render (root);
            assertEquals(expected, actual);
        }
    }

    /* 2013-08-29 FHR -- This approach is deprecated. Use an ASTNodeTransformer or ASTNodeRenderer instead.
    // Right now doesn't test anything but constant and function
    // overrides.  In future should test var and op nodes too.
    @Test
    public void testValueOverrides() throws ParseException {
        String[] rdShorts = {
            "2.0",
            "x",
            "y = m * x + b",
            "2.0 + 2.0",
            "2.0 % t.y * u$$ && !p__.$f",
            "cos(y)",
            "V' += m + a' - 35.0 + cos(v$)",
            "foo(x) = f ^ 2.0 - (1.03E21 ^ tan(3.0) + u.t) * 22.0 - o[3.0, r]",
            "u__4 = 2.0 - cos(h += 5.0, cos(rr) * t[r] / \"earth\") * t$.f0 ^ (6.0 + !k)",
            "x[3.0 + a + cos(derek(1.0)), 9.0] = b = c = 0.0",
            "a = \"dog\" ^ derek(a = 2.0, 3000.0, cos(x - 3.0))",
            "x = 3.0 * x ^ (2.0 + 3.0) - t ^ cos(theta')",
            "yy = !false + 4.0 - (true == false)",
            "alpha_m = (25 - V) / (10 * (exp((25 - V) / 10) - 1))",

            // Precedence
            "x = 333 - 4 / 6 + 7",
            "x = (333 - 4) / 6 + 7",
            "x = 333 - 4 / (6 + 7)",
            "x = (333 - 4) / (6 + 7)",
            "x = 333 * 4 / 6 % 7",
            "x = 333 * 4 / 6 % 7",
            "x = 333 * 4 / (6 % 7)",
            "x = 333 * 4 / (6 % 7)",
        };

        ASTNodeValueOverriderMap oMap = new ASTNodeValueOverriderMap();

        // Replace all value-occurrences of numeric literal 3 with 333.
        oMap.put(ASTConstant.class, new ASTNodeValueOverrider() {
            @Override
            public Object getValue(ASTNodeBase node) {
                if(node.getValue().equals(new Long(3))) {
                    return 333;
                }
                return node.getValue();
            }
        });

        // Replace all value-occurrences of function "tanh" with "cos".
        oMap.put(ASTFunNode.class, new ASTNodeValueOverrider() {
            @Override
            public Object getValue(ASTNodeBase node) {
                Function func = ((ASTFunNode) node).getFunction();
                if(func.getName().equals("tanh")) {
                    return new CosineFunction();
                }
                return node.getValue();
            }
        });

        ASTRenderingContext context = new ASTRenderingContext(true, null, oMap);

        for(int e = 0; e < testExprs.length; e++) {
            String expected = rdShorts[e];
            ASTNodeBase root = ExpressionParser.parse(testExprs[e]);
            String actual = context.render (root);
            assertEquals(expected, actual);
        }
    }
    */

    // TODO: Demonstrates only -- doesn't actually test yet.
    @Test
    public void testTransform() throws ParseException {
        String s = "alpha_m = (25 - V) / (10 * (exp ((25 - V) / 10) - 1))";
        ParsedEquation pe = EquationParser.parse(s);
        //System.out.println("BEFORE: " + pe);
        ASTTransformationContext context = new ASTTransformationContext ();
        context.add (ASTConstant.class, new ASTNodeTransformer () {
            public ASTNodeBase transform (ASTNodeBase node) {
                if(node.getValue() instanceof Number) {
                    Double i = (Double) node.getValue();
                    node.setValue(i + 5);
                }
                return node;
            }
        });
        pe.getTree().transform(context);
        //System.out.println("AFTER: " + pe);
    }

    @Test
    public void testIsValidVariableName() {
        Object[] values = {
            // NAME      (.)   ($)   (')   RESULT
            "",          true, true, true, false,
            " ",         true, true, true, false,
            "123",       true, true, true, false,
            "!@#$",      true, true, true, false,
            "ABC",       true, true, true, true,
            "abc",       true, true, true, true,
            "_abc",      true, true, true, true,
            "abc'",      true, true, false, false,
            "abc'",      true, true, true, true,
            "'a",        true, true, true, false,
            "ab'c'",     true, true, true, false,
            "abc_123",   true, true, true, true,
            "abc_$123",  true, true, true, true,
            "$$$$$$$$",  true, true, true, true,
            "abc_$123",  true, false, true, false,
            "abc_.123",  true, true, true, false,
            "abc_.a123", true, true, true, true,
            "abc_.a12.", true, true, true, false,
            "abc_.a123", false, true, true, false,
            ".abc",      true, true, true, false,
            "1.abc",     true, true, true, false,
        };

        for(int e = 0; e < values.length; e += 5) {
            String name = (String) values[e];
            boolean allowDot = (Boolean) values[e + 1];
            boolean allowDollar = (Boolean) values[e + 2];
            boolean allowTickMark = (Boolean) values[e + 3];
            boolean expected = (Boolean) values[e + 4];
            assertEquals(expected,
                ASTVarNode.isValidVariableName(
                    name, allowDot, allowDollar, allowTickMark));
        }

        try {
            ASTVarNode.isValidVariableName(null, false, false, false);
        } catch(NullPointerException e) {
            return;
        }
        fail();
    }


    // There are two ways to currently change the exponentiation
    // operator:
    //   1.  Add a custom renderer for all OpNodes.  This means
    //       you have to provide the entire rendering for all
    //       OpNodes, and just boils down to copy and pasting
    //       the entire method into the custom render save
    //       for the part where you want a different string
    //       for just the value if it's a ^ OpNode.
    //   2.  Add a value overrider for all OpNodes.  This will
    //       replace the value of just the ^ OpNode with another
    //       function ** which is identical save for the name.
    //       This is also suboptimal because now there's this
    //       identical function ExponentiationAltFunction that
    //       exists just for custom rendering purposes.
    //
    // The third possible (unimplemented) option would be to
    // allow for a way to provide custom renderers for JUST
    // the VALUE of a node.  That would be the best of both
    // worlds, whereas the above 2 options are both extremes
    // of a certain sort.
    @Test
    public void testChangeExponentiationOperator() throws ParseException {
        String expr = "x = t ^ 8 - h ^ cos(r) + (5 / (8 * 9))";
        String expected = "x = t ** 8 - h ** cos(r) + 5 / (8 * 9)";

        // Change rendering of operators to render like functions (prefix notation).
        /*rMap.put(ASTOpNode.class, new ASTNodeRenderer() {
            public String render(ASTNodeBase node, ASTRenderingContext context) {
                ASTOpNode opNode = (ASTOpNode) node;

                // Long rendering
                if(!context.isShortMode()) {
                    String ret = "";
                    if(opNode.getValue() instanceof ListSubscriptExpression) {
                        ret += context.renderChild(opNode.getChild(0));
                        ret += "[" + context.renderChild(opNode.getChild(1)) + "]";
                    } else if(opNode.getCount() == 1) {
                        ret += opNode.getValue();
                        ret += context.renderChild(opNode.getChild(0));
                    } else {
                        if(!(opNode.getParent() instanceof ASTStart)) {
                            ret += "(";
                        }
                        ret += context.renderChild(opNode.getChild(0));
                        if(opNode.getValue().equals(FunctionList.get(FunctionList.OP_POWER))) {
                            ret += " ** ";
                        } else {
                            ret += " " + opNode.getValue() + " ";
                        }
                        ret += context.renderChild(opNode.getChild(1));
                        if(!(opNode.getParent() instanceof ASTStart)) {
                            ret += ")";
                        }
                    }
                    return ret;
                }

                // Short rendering
                String ret = "";
                if(opNode.getValue() instanceof ListSubscriptExpression) {
                    ret += context.renderChild(opNode.getChild(0));
                    ret += "[" + context.renderChild(opNode.getChild(1)) + "]";
                } else if(opNode.getCount() == 1) {
                    if(!(opNode.getValue() instanceof UnaryPlusFunction)) {
                        ret += opNode.getValue();
                    }
                    ret += context.renderChild(opNode.getChild(0));
                } else {
                    boolean useParens;
                    Function thisFunc = (Function) opNode.getValue();
                    int thisPrecLevel = FunctionList.getPrecedenceLevel(thisFunc);
                    Associativity thisAssoc = FunctionList.getAssociativity(thisFunc);

                    // Left-hand child
                    useParens = false;
                    if(opNode.getChild(0) instanceof ASTOpNode) {
                        int rightPrecLevel = FunctionList.getPrecedenceLevel(
                            (Function) opNode.getChild(0).getValue());
                        if(thisPrecLevel > rightPrecLevel ||
                                thisPrecLevel == rightPrecLevel &&
                                thisAssoc == Associativity.RIGHT_TO_LEFT) {
                            useParens = true;
                        }
                    }
                    if(useParens) {
                        ret += "(";
                    }
                    ret += context.renderChild(opNode.getChild(0));
                    if(useParens) {
                        ret += ")";
                    }

                    if(opNode.getValue().equals(FunctionList.get(FunctionList.OP_POWER))) {
                        ret += " ** ";
                    } else {
                        ret += " " + opNode.getValue() + " ";
                    }

                    // Right-hand child
                    useParens = false;
                    if(opNode.getChild(1) instanceof ASTOpNode) {
                        int rightPrecLevel = FunctionList.getPrecedenceLevel(
                            (Function) opNode.getChild(1).getValue());
                        if(thisPrecLevel > rightPrecLevel ||
                                thisPrecLevel == rightPrecLevel &&
                                thisAssoc == Associativity.LEFT_TO_RIGHT) {
                            useParens = true;
                        }
                    }
                    if(useParens) {
                        ret += "(";
                    }
                    ret += context.renderChild(opNode.getChild(1));
                    if(useParens) {
                        ret += ")";
                    }
                }
                return ret;
            }
        });*/

        ASTRenderingContext context = new ASTRenderingContext (true);
        context.add (ASTOpNode.class, new ASTNodeRenderer() {
            @Override
            public String render (ASTNodeBase node, ASTRenderingContext context) {
                if(node.getValue() instanceof ExponentiationFunction) {
                    return context.render (node.getChild (0)) + " ** " + context.render (node.getChild (1));
                }
                return node.render (context);
            }
        });
        ASTNodeBase root = ExpressionParser.parse(expr);
        String actual = context.render (root);
        assertEquals(expected, actual);
    }

    /*
     * 2013-08-24 FHR -- Assignment to functions is forbidden. So is the use of boolean literals. If anyone cares
     *                   to rework this test to meet those requirements, they are welcome to it.
    @Test
    public void testTreeStructureAndContents() throws ParseException {
        String expr = "foo(x, \"A\" + [1,2;3,4], t.j'[4.0]) = f$'' ^ 2.0 - (10.3e20 ^ cos(3.0) + u.t) * true - o[3.0, r]";
        ASTNodeBase n = ExpressionParser.parse(expr);

        // foo(x, "A", t.j'[4.0]) = f$'' ^ 2.0 - (10.3e20 ^ cos(3.0) + u.t) * true - o[3.0, r]
        checkOpNode(n, "=", AssignmentFunction.class,
            FunctionList.OP_ASSIGN,
            "foo(x, (\"A\" + [1, 2; 3, 4]), t.j'[4.0]) = (((f$'' ^ 2.0) - (((1.03E21 ^ cos(3.0)) + u.t) * true)) - o[3.0, r])",
            "foo(x, \"A\" + [1, 2; 3, 4], t.j'[4.0]) = f$'' ^ 2.0 - (1.03E21 ^ cos(3.0) + u.t) * true - o[3.0, r]",
            2);

        // foo(x, "A" + [1,2;3,4], t.j'[4.0])
        ASTNodeBase n_0 = n.getChild(0);
        checkFunNode(n_0, "foo", UnknownFunction.class, null,
            "foo(x, (\"A\" + [1, 2; 3, 4]), t.j'[4.0])",
            "foo(x, \"A\" + [1, 2; 3, 4], t.j'[4.0])",
            3);

        // x
        ASTNodeBase n_0_0 = n_0.getChild(0);
        checkVarNode(n_0_0, "x", 0, "x");

        // "A" + [1,2;3,4]
        ASTNodeBase n_0_1 = n_0.getChild(1);
        checkOpNode(n_0_1, "+", AdditionFunction.class,
            FunctionList.OP_ADD,
            "(\"A\" + [1, 2; 3, 4])",
            "\"A\" + [1, 2; 3, 4]",
            2);

        // "A"
        ASTNodeBase n_0_1_0 = n_0_1.getChild(0);
        checkConNode(n_0_1_0, "A", String.class, "A", "\"A\"");

        // [1,2;3,4]
        ASTNodeBase n_0_1_1 = n_0_1.getChild(1);
        checkMatrixNode(n_0_1_1, 2, 2,
            "[1, 2; 3, 4]",
            "[1, 2; 3, 4]",
            new String[][]{{"1", "2"},{"3", "4"}});

        // t.j'[4.0]
        ASTNodeBase n_0_2 = n_0.getChild(2);
        checkOpNode(n_0_2, "[]", ListSubscriptExpression.class,
            FunctionList.OP_ELEMENT, "t.j'[4.0]", "t.j'[4.0]", 2);

        // t.j'
        ASTNodeBase n_0_2_0 = n_0_2.getChild(0);
        checkVarNode(n_0_2_0, "t.j'", 1, "t.j");

        // [4.0]
        ASTNodeBase n_0_2_1 = n_0_2.getChild(1);
        checkListNode(n_0_2_1, 1, "4.0");

        // 4.0
        ASTNodeBase n_0_2_1_0 = n_0_2_1.getChild(0);
        checkConNode(n_0_2_1_0, "4.0", Double.class, 4.0, "4.0");

        // f$'' ^ 2.0 - (10.3e20 ^ cos(3.0) + u.t) * true - o[3.0, r]
        ASTNodeBase n_1 = n.getChild(1);
        checkOpNode(n_1, "-", SubtractionFunction.class,
            FunctionList.OP_SUBTRACT,
            "(((f$'' ^ 2.0) - (((1.03E21 ^ cos(3.0)) + u.t) * true)) - o[3.0, r])",
            "f$'' ^ 2.0 - (1.03E21 ^ cos(3.0) + u.t) * true - o[3.0, r]",
            2);

        // f$'' ^ 2.0 - (10.3e20 ^ cos(3.0) + u.t) * true
        ASTNodeBase n_1_0 = n_1.getChild(0);
        checkOpNode(n_1_0, "-", SubtractionFunction.class,
            FunctionList.OP_SUBTRACT,
            "((f$'' ^ 2.0) - (((1.03E21 ^ cos(3.0)) + u.t) * true))",
            "f$'' ^ 2.0 - (1.03E21 ^ cos(3.0) + u.t) * true",
            2);

        // f$'' ^ 2.0
        ASTNodeBase n_1_0_0 = n_1_0.getChild(0);
        checkOpNode(n_1_0_0, "^", ExponentiationFunction.class,
            FunctionList.OP_POWER,
            "(f$'' ^ 2.0)",
            "f$'' ^ 2.0",
            2);

        // f$''
        ASTNodeBase n_1_0_0_0 = n_1_0_0.getChild(0);
        checkVarNode(n_1_0_0_0, "f$''", 2, "f$");

        // 2.0
        ASTNodeBase n_1_0_0_1 = n_1_0_0.getChild(1);
        checkConNode(n_1_0_0_1, "2.0", Double.class, 2.0, "2.0");

        // (10.3e20 ^ cos(3.0) + u.t) * true
        ASTNodeBase n_1_0_1 = n_1_0.getChild(1);
        checkOpNode(n_1_0_1, "*", MultiplicationFunction.class,
            FunctionList.OP_MULTIPLY,
            "(((1.03E21 ^ cos(3.0)) + u.t) * true)",
            "(1.03E21 ^ cos(3.0) + u.t) * true",
            2);

        // 10.3e20 ^ cos(3.0) + u.t
        ASTNodeBase n_1_0_1_0 = n_1_0_1.getChild(0);
        checkOpNode(n_1_0_1_0, "+", AdditionFunction.class,
            FunctionList.OP_ADD,
            "((1.03E21 ^ cos(3.0)) + u.t)",
            "1.03E21 ^ cos(3.0) + u.t",
            2);

        // 10.3e20 ^ cos(3.0)
        ASTNodeBase n_1_0_1_0_0 = n_1_0_1_0.getChild(0);
        checkOpNode(n_1_0_1_0_0, "^", ExponentiationFunction.class,
            FunctionList.OP_POWER,
            "(1.03E21 ^ cos(3.0))",
            "1.03E21 ^ cos(3.0)",
            2);

        // 10.3e20
        ASTNodeBase n_1_0_1_0_0_0 = n_1_0_1_0_0.getChild(0);
        checkConNode(n_1_0_1_0_0_0, "1.03E21", Double.class, 10.3e20, "1.03E21");

        // cos(3.0)
        ASTNodeBase n_1_0_1_0_0_1 = n_1_0_1_0_0.getChild(1);
        checkFunNode(n_1_0_1_0_0_1, "cos", CosineFunction.class, "cos",
            "cos(3.0)", "cos(3.0)", 1);

        // 3.0
        ASTNodeBase n_1_0_1_0_0_1_0 = n_1_0_1_0_0_1.getChild(0);
        checkConNode(n_1_0_1_0_0_1_0, "3.0", Double.class, 3.0, "3.0");

        // u.t
        ASTNodeBase n_1_0_1_0_1 = n_1_0_1_0.getChild(1);
        checkVarNode(n_1_0_1_0_1, "u.t", 0, "u.t");

        // true
        ASTNodeBase n_1_0_1_1 = n_1_0_1.getChild(1);
        checkConNode(n_1_0_1_1, "true", Boolean.class, true, "true");

        // o[3.0, r]
        ASTNodeBase n_1_1 = n_1.getChild(1);
        checkOpNode(n_1_1, "[]", ListSubscriptExpression.class,
            FunctionList.OP_ELEMENT, "o[3.0, r]", "o[3.0, r]", 2);

        // o
        ASTNodeBase n_1_1_0 = n_1_1.getChild(0);
        checkVarNode(n_1_1_0, "o", 0, "o");

        // [3.0, r]
        ASTNodeBase n_1_1_1 = n_1_1.getChild(1);
        checkListNode(n_1_1_1, 2, "3.0", "r");

        // 3.0
        ASTNodeBase n_1_1_1_0 = n_1_1_1.getChild(0);
        checkConNode(n_1_1_1_0, "3.0", Double.class, 3.0, "3.0");

        // r
        ASTNodeBase n_1_1_1_1 = n_1_1_1.getChild(1);
        checkVarNode(n_1_1_1_1, "r", 0, "r");
    }
    */

    private void checkOpNode(ASTNodeBase node, String ts, Class<?> opClass, String fName, String rdLong, String rdShort, int cnt) {
        assertTrue(node instanceof ASTOpNode);
        assertEquals(ts, node.toString());
        assertTrue(node.getValue().getClass().equals(opClass));
        assertEquals(FunctionList.get(fName),
            ((ASTOpNode) node).getFunction());
        assertEquals(rdLong, node.toReadableLong());
        assertEquals(rdShort, node.toReadableShort());
        assertEquals(cnt, node.getCount());
    }

    private void checkFunNode(ASTNodeBase node, String ts, Class<?> opClass, String fName, String rdLong, String rdShort, int cnt) {
        assertTrue(node instanceof ASTFunNode);
        assertEquals(ts, node.toString());
        assertTrue(node.getValue().getClass().equals(opClass));
        if(fName != null) {
            assertEquals(FunctionList.get(fName),
                ((ASTFunNode) node).getFunction());
        }
        assertEquals(rdLong, node.toReadableLong());
        assertEquals(rdShort, node.toReadableShort());
        assertEquals(cnt, node.getCount());
    }

    private void checkVarNode(ASTNodeBase node, String ts, int order, String varN) {
        assertTrue(node instanceof ASTVarNode);
        assertEquals(ts, node.toString());
        assertEquals(order, ((ASTVarNode) node).getOrder());
        assertEquals(varN, ((ASTVarNode) node).getVariableName());
        assertEquals(varN, node.getValue());
        assertEquals(0, node.getCount());
    }

    private void checkConNode(ASTNodeBase node, String ts, Class<?> valClass, Object val, String rd) {
        assertTrue(node instanceof ASTConstant);
        assertTrue(node.getValue().getClass().equals(valClass));
        assertEquals(ts, node.toString());
        assertEquals(val, node.getValue());
        assertEquals(rd, node.toReadableLong());
        assertEquals(rd, node.toReadableShort());
        assertEquals(0, node.getCount());
    }

    private void checkListNode(ASTNodeBase node, int cnt, String... elemRenderShort) {
        assertTrue(node instanceof ASTListNode);
        assertEquals("LIST", node.toString());
        assertEquals("LIST", node.getValue());
        assertEquals(cnt, node.getCount());
        assertEquals(cnt, elemRenderShort.length);
        for(int i = 0; i < cnt; i++) {
            assertEquals(node.getChild(i).toReadableShort(), elemRenderShort[i]);
        }
    }

    /*
    private void checkMatrixNode(ASTNodeBase node, int rows, int cols, String rdLong, String rdShort, String[][] elemRenderShort) {
        assertTrue(node instanceof ASTMatrixNode);
        assertEquals("MATRIX", node.toString());
        assertEquals("MATRIX", node.getValue());
        assertEquals(rdLong, node.toReadableLong());
        assertEquals(rdShort, node.toReadableShort());
        assertEquals(rows * cols, node.getCount());
        assertEquals(rows, elemRenderShort.length);
        assertEquals(cols, elemRenderShort[0].length);
        assertEquals(rows, ((ASTMatrixNode) node).getRows());
        assertEquals(cols, ((ASTMatrixNode) node).getColums());
        int index = 0;
        for(int row = 0; row < rows; row++) {
            for(int col = 0; col < cols; col++, index++) {
                assertTrue(node.getChild(index) == ((ASTMatrixNode) node).getChildMatrix()[row][col]);
                assertEquals(node.getChild(index).toReadableShort(), elemRenderShort[row][col]);
            }
        }
    }
    */
}
