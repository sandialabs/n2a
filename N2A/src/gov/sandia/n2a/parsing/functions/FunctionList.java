/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.parsing.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;

public class FunctionList {

    private static class PrecedenceManager {

        private List<List<Function>> levels = new ArrayList<List<Function>>();
        private List<Associativity> levelAssociativity = new ArrayList<Associativity>();

        public PrecedenceManager addLevel(Associativity associativity, String... funcNames) {
            List<Function> level = new ArrayList<Function>();
            for(String funcName : funcNames) {
                level.add(get(funcName));
            }
            levels.add(level);
            levelAssociativity.add(associativity);
            return this;
        }
        public int getLevel(Function func) {
            for(int lvl = 0; lvl < levels.size(); lvl++) {
                if(levels.get(lvl).contains(func)) {
                    return lvl;
                }
            }
            return -1;  // Denotes does not exist in manager.
        }
        public Associativity getAssociativity(Function func) {
            for(int lvl = 0; lvl < levels.size(); lvl++) {
                if(levels.get(lvl).contains(func)) {
                    return levelAssociativity.get(lvl);
                }
            }
            return null;  // Denotes does not exist in manager.
        }
    }
    private static PrecedenceManager precedenceMgr = new PrecedenceManager();

    public static String OP_ADD      = "+";
    public static String OP_SUBTRACT = "-";
    public static String OP_MULTIPLY = "*";
    public static String OP_DIVIDE   = "/";
    public static String OP_MOD      = "%";
    public static String OP_POWER    = "^";
    public static String OP_UPLUS    = "-UP";
    public static String OP_UMINUS   = "-UM";

    public static String OP_AND      = "&&";
    public static String OP_OR       = "||";
    public static String OP_NOT      = "!";

    public static String OP_EQ       = "==";
    public static String OP_NE       = "!=";

    public static String OP_LT       = "<";
    public static String OP_LE       = "<=";
    public static String OP_GT       = ">";
    public static String OP_GE       = ">=";

    public static String OP_ASSIGN   = "=";
    public static String OP_AASSIGN  = "+=";
    public static String OP_CASSIGN  = ":=";

    public static String OP_DOT      = ".";
    public static String OP_CROSS    = "^^";

    public static String OP_LIST     = "LIST";
    public static String OP_ELEMENT  = "[]";

    private static Map<String, Function> functions = new HashMap<String, Function>();
    private static void addFunc(String name, Function func) {
        functions.put(name, func);
    }

    static {

        // Arithmetic
        addFunc(OP_ADD,      new AdditionFunction());
        addFunc(OP_SUBTRACT, new SubtractionFunction());
        addFunc(OP_MULTIPLY, new MultiplicationFunction());
        addFunc(OP_DIVIDE,   new DivisionFunction());
        addFunc(OP_MOD,      new ModulusFunction());
        addFunc(OP_POWER,    new ExponentiationFunction());
        addFunc(OP_UPLUS,    new UnaryPlusFunction());
        addFunc(OP_UMINUS,   new UnaryMinusFunction());

        // Logical
        addFunc(OP_AND,      new LogicalAndFunction());
        addFunc(OP_OR,       new LogicalOrFunction());
        addFunc(OP_NOT,      new LogicalNotFunction());

        // Comparison
        addFunc(OP_EQ,       new ComparisonEqualsFunction());
        addFunc(OP_NE,       new ComparisonNotEqualsFunction());

        // Relational
        addFunc(OP_LT,       new RelationalLTFunction());
        addFunc(OP_LE,       new RelationalLEFunction());
        addFunc(OP_GT,       new RelationalGTFunction());
        addFunc(OP_GE,       new RelationalGEFunction());

        // Assignment & Compound Assignment
        addFunc(OP_ASSIGN,   new AssignmentFunction());
        addFunc(OP_CASSIGN,  new AssignmentFunction());
        addFunc(OP_AASSIGN,  new AdditionAssignmentFunction());

        // List
        addFunc(OP_ELEMENT,  new ListSubscriptExpression());

        // Vector (not impl)
        addFunc(OP_DOT,      new DotFunction());
        addFunc(OP_CROSS,    new CrossFunction());

        // Other
        addFunc("sin",       new SineFunction());
        addFunc("cos",       new CosineFunction());
        addFunc("tan",       new TangentFunction());
        addFunc("exp",       new ExponentialFunction());
        addFunc("uniform",   new UniformDistributionFunction());
        addFunc("gaussian",  new GaussianDistributionFunction());
        addFunc("lognormal", new LognormalDistributionFunction());
        addFunc("trace",     new TraceFunction());

        precedenceMgr.addLevel(Associativity.RIGHT_TO_LEFT, OP_ASSIGN, OP_AASSIGN, OP_CASSIGN)  // Level 0
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_OR)
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_AND)
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_EQ, OP_NE)
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_LT, OP_LE, OP_GT, OP_GE)
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_ADD, OP_SUBTRACT)
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_MULTIPLY, OP_DIVIDE, OP_MOD)
                     .addLevel(Associativity.RIGHT_TO_LEFT, OP_POWER)
                     .addLevel(Associativity.RIGHT_TO_LEFT, OP_UPLUS, OP_UMINUS, OP_NOT)
                     .addLevel(Associativity.LEFT_TO_RIGHT, OP_ELEMENT);
    }

    public static void initFromPlugins() {
        List<ExtensionPoint> exts = PluginManager.getExtensionsForPoint(Function.class);
        for(ExtensionPoint ext : exts) {
            Function f = (Function) ext;
            addFunc(f.getName(), f);
        }
    }

    // All functions not registered in the precedence manager
    // have the same, highest precedence level.  Example:
    //    4 + 5 + cos(x) * sin(y)
    public static int getPrecedenceLevel(Function thisFunc) {
        int level = precedenceMgr.getLevel(thisFunc);
        return level == -1 ? Integer.MAX_VALUE : level;
    }
    public static Associativity getAssociativity(Function thisFunc) {
        return precedenceMgr.getAssociativity(thisFunc);
    }

    public static boolean contains(String name) {
        return functions.get(name) != null;
    }
    public static Function get(String name) {
        return functions.get(name);
    }
    public static Function getOrUnknown(String name) {
        Function func = functions.get(name);
        if(func == null) {
            func = new UnknownFunction(name);
        }
        return func;
    }

    public static void main(String[] args) {
        for(String key : functions.keySet()) {
            Function func = functions.get(key);
            System.out.println(key + " (" + func.getDescription() + ")");
            for(ParameterSet set : func.getAllowedParameterSets()) {
                System.out.println("    " + set);
            }
        }
    }
}
