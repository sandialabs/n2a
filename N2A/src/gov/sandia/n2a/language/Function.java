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
import gov.sandia.n2a.language.op.AssignAppend;
import gov.sandia.n2a.language.op.Cosine;
import gov.sandia.n2a.language.op.Divide;
import gov.sandia.n2a.language.op.EQ;
import gov.sandia.n2a.language.op.Exp;
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
import gov.sandia.n2a.language.op.Power;
import gov.sandia.n2a.language.op.Sine;
import gov.sandia.n2a.language.op.Subtract;
import gov.sandia.n2a.language.op.Tangent;
import gov.sandia.n2a.language.op.Trace;
import gov.sandia.n2a.language.op.Uniform;

import java.util.List;
import java.util.TreeSet;

import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;

public class Function implements ExtensionPoint, Comparable<Function>
{
    public enum Associativity
    {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
    }

    public String        name;
    public Associativity associativity;
    public int           precedence;
    public boolean       assignment;  ///< We change the value of our first argument.
    public boolean       output;      ///< We produce some side effect that informs the user.

    public Function ()
    {
        this ("unknown");
    }

    public Function (String name)
    {
        this.name     = name;
        associativity = Associativity.LEFT_TO_RIGHT;
        precedence    = 1;
        //assignment    = false;
        //output        = false;
    }

    public Object eval (Object[] args) throws EvaluationException
    {
        throw new EvaluationException ("Function '" + name + "' not implemented.");
    }

    public String toString ()
    {
        return name;
    }

    public int compareTo (Function that)
    {
        return name.compareTo (that.name);
    }

    // Static interface ------------------------------------------------------

    public static TreeSet<Function> functions = new TreeSet<Function> ();

    static
    {
        functions.add (new Add ());
        functions.add (new AND ());
        functions.add (new Assign ());
        functions.add (new AssignAdd ());
        functions.add (new AssignAppend ());
        functions.add (new Cosine ());
        functions.add (new Divide ());
        functions.add (new EQ ());
        functions.add (new Exp ());
        functions.add (new Gauss ());
        functions.add (new GE ());
        functions.add (new GT ());
        functions.add (new LE ());
        functions.add (new Lognormal ());
        functions.add (new LT ());
        functions.add (new Modulo ());
        functions.add (new Multiply ());
        functions.add (new NE ());
        functions.add (new Negate ());
        functions.add (new NOT ());
        functions.add (new OR ());
        functions.add (new Power ());
        functions.add (new Sine ());
        functions.add (new Subtract ());
        functions.add (new Tangent ());
        functions.add (new Trace ());
        functions.add (new Uniform ());
    }

    public static void initFromPlugins ()
    {
        List<ExtensionPoint> extensions = PluginManager.getExtensionsForPoint (Function.class);
        for (ExtensionPoint e : extensions) functions.add ((Function) e);
    }

    public static Function get (String name)
    {
        return get (name, false);
    }

    public static Function get (String name, boolean create)
    {
        Function query = new Function (name);
        Function result = functions.floor (query);
        if (result != null  &&  result.compareTo (query) == 0) return result;
        if (create) return query;
        return null;
    }
}
