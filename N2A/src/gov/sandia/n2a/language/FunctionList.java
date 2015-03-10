/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.language;

import gov.sandia.n2a.language.op.AND;
import gov.sandia.n2a.language.op.Add;
import gov.sandia.n2a.language.op.Assign;
import gov.sandia.n2a.language.op.AssignAdd;
import gov.sandia.n2a.language.op.Cosine;
import gov.sandia.n2a.language.op.Divide;
import gov.sandia.n2a.language.op.EQ;
import gov.sandia.n2a.language.op.Exp;
import gov.sandia.n2a.language.op.Power;
import gov.sandia.n2a.language.op.GE;
import gov.sandia.n2a.language.op.GT;
import gov.sandia.n2a.language.op.Gauss;
import gov.sandia.n2a.language.op.LE;
import gov.sandia.n2a.language.op.LT;
import gov.sandia.n2a.language.op.Lognormal;
import gov.sandia.n2a.language.op.Modulo;
import gov.sandia.n2a.language.op.Multiply;
import gov.sandia.n2a.language.op.NE;
import gov.sandia.n2a.language.op.NOT;
import gov.sandia.n2a.language.op.Negate;
import gov.sandia.n2a.language.op.OR;
import gov.sandia.n2a.language.op.Sine;
import gov.sandia.n2a.language.op.Subtract;
import gov.sandia.n2a.language.op.Tangent;
import gov.sandia.n2a.language.op.Trace;
import gov.sandia.n2a.language.op.Uniform;
import gov.sandia.n2a.language.op.UnknownFunction;

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
        addFunc(OP_ADD,      new Add());
        addFunc(OP_SUBTRACT, new Subtract());
        addFunc(OP_MULTIPLY, new Multiply());
        addFunc(OP_DIVIDE,   new Divide());
        addFunc(OP_MOD,      new Modulo());
        addFunc(OP_POWER,    new Power());
        addFunc(OP_UMINUS,   new Negate());

        // Logical
        addFunc(OP_AND,      new AND());
        addFunc(OP_OR,       new OR());
        addFunc(OP_NOT,      new NOT());

        // Comparison
        addFunc(OP_EQ,       new EQ());
        addFunc(OP_NE,       new NE());

        // Relational
        addFunc(OP_LT,       new LT());
        addFunc(OP_LE,       new LE());
        addFunc(OP_GT,       new GT());
        addFunc(OP_GE,       new GE());

        // Assignment & Compound Assignment
        addFunc(OP_ASSIGN,   new Assign());
        addFunc(OP_CASSIGN,  new Assign());
        addFunc(OP_AASSIGN,  new AssignAdd());

        // Other
        addFunc("sin",       new Sine());
        addFunc("cos",       new Cosine());
        addFunc("tan",       new Tangent());
        addFunc("exp",       new Exp());
        addFunc("uniform",   new Uniform());
        addFunc("gaussian",  new Gauss());
        addFunc("lognormal", new Lognormal());
        addFunc("trace",     new Trace());

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
