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
import java.util.TreeMap;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;

public class Operator
{
    public enum Associativity
    {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
    }

    /// Example implementation of function to register Operator
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "unknown";
            }

            public Operator createInstance ()
            {
                return new Operator ();
            }
        };
    }

    public Associativity associativity ()
    {
        return Associativity.LEFT_TO_RIGHT;
    }

    public int precedence ()
    {
        return 1;
    }

    /// Indicates if we produce some side effect that informs the user.
    public boolean output ()
    {
        return false;
    }

    public Type eval (EvaluationContext context) throws EvaluationException
    {
        // Note: All allowable operators must be implemented by the internal simulator.
        // This includes operators provided extensions. No free-form functions are allowed.
        throw new EvaluationException ("Operator not implemented.");
    }

    public String toString ()
    {
        return "unknown";
    }

    // Static interface ------------------------------------------------------

    public interface Factory extends ExtensionPoint
    {
        public String   name ();  ///< Unique string for searching in the table of registered operators. Used explicitly by parser.
        public Operator createInstance ();  ///< Operators may be instantiated with specific operands. The operands must be set separately based on category (Unary, Binary, Function)
    }

    public static TreeMap<String,Factory> operators = new TreeMap<String,Factory> ();

    public static void register (Factory f)
    {
        operators.put (f.name (), f);
    }

    static
    {
        // Functions
        register (AbsoluteValue.factory ());
        register (Cosine       .factory ());
        register (Exp          .factory ());
        register (Gaussian     .factory ());
        register (Grid         .factory ());
        register (Norm         .factory ());
        register (Pulse        .factory ());
        register (ReadMatrix   .factory ());
        register (Sine         .factory ());
        register (Tangent      .factory ());
        register (Trace        .factory ());
        register (Uniform      .factory ());

        // Operators
        register (Add      .factory ());
        register (AND      .factory ());
        register (Divide   .factory ());
        register (EQ       .factory ());
        register (GE       .factory ());
        register (GT       .factory ());
        register (LE       .factory ());
        register (LT       .factory ());
        register (Modulo   .factory ());
        register (Multiply .factory ());
        register (NE       .factory ());
        register (Negate   .factory ());
        register (NOT      .factory ());
        register (OR       .factory ());
        register (Power    .factory ());
        register (Subtract .factory ());
        register (Transpose.factory ());
    }

    public static void initFromPlugins ()
    {
        List<ExtensionPoint> extensions = PluginManager.getExtensionsForPoint (Factory.class);
        for (ExtensionPoint e : extensions) register ((Factory) e);
    }

    public static Operator get (String name)
    {
        Factory f = operators.get (name);
        if (f != null) return f.createInstance ();
        return new Operator ();  // poisoned operator
    }
}
