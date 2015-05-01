/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.language.function.AbsoluteValue;
import gov.sandia.n2a.language.function.Cosine;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Grid;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Pulse;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Sine;
import gov.sandia.n2a.language.function.Tangent;
import gov.sandia.n2a.language.function.Trace;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.AND;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.operator.EQ;
import gov.sandia.n2a.language.operator.GE;
import gov.sandia.n2a.language.operator.GT;
import gov.sandia.n2a.language.operator.LE;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.operator.Modulo;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.operator.NE;
import gov.sandia.n2a.language.operator.NOT;
import gov.sandia.n2a.language.operator.Negate;
import gov.sandia.n2a.language.operator.OR;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.operator.Subtract;
import gov.sandia.n2a.language.operator.Transpose;

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

    public Type eval (Type[] args) throws EvaluationException
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
        // Functions
        functions.add (new AbsoluteValue ());
        functions.add (new Cosine ());
        functions.add (new Exp ());
        functions.add (new Gaussian ());
        functions.add (new Grid ());
        functions.add (new Norm ());
        functions.add (new Pulse ());
        functions.add (new ReadMatrix ());
        functions.add (new Sine ());
        functions.add (new Tangent ());
        functions.add (new Trace ());
        functions.add (new Uniform ());

        // Operators
        functions.add (new Add ());
        functions.add (new AND ());
        functions.add (new Divide ());
        functions.add (new EQ ());
        functions.add (new GE ());
        functions.add (new GT ());
        functions.add (new LE ());
        functions.add (new LT ());
        functions.add (new Modulo ());
        functions.add (new Multiply ());
        functions.add (new NE ());
        functions.add (new Negate ());
        functions.add (new NOT ());
        functions.add (new OR ());
        functions.add (new Power ());
        functions.add (new Subtract ());
        functions.add (new Transpose ());
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
