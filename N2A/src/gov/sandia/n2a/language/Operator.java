/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.function.AbsoluteValue;
import gov.sandia.n2a.language.function.Cosine;
import gov.sandia.n2a.language.function.DrawDisc;
import gov.sandia.n2a.language.function.DrawSegment;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Floor;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Grid;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.function.Max;
import gov.sandia.n2a.language.function.Min;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Round;
import gov.sandia.n2a.language.function.Signum;
import gov.sandia.n2a.language.function.Sine;
import gov.sandia.n2a.language.function.SquareRoot;
import gov.sandia.n2a.language.function.Tangent;
import gov.sandia.n2a.language.function.Output;
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
import gov.sandia.n2a.language.operator.MultiplyElementwise;
import gov.sandia.n2a.language.operator.NE;
import gov.sandia.n2a.language.operator.NOT;
import gov.sandia.n2a.language.operator.Negate;
import gov.sandia.n2a.language.operator.OR;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.operator.Subtract;
import gov.sandia.n2a.language.operator.Transpose;
import gov.sandia.n2a.language.parse.ASTConstant;
import gov.sandia.n2a.language.parse.ASTIdentifier;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.ASTMatrix;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.parse.ASTOperator;
import gov.sandia.n2a.language.parse.ExpressionParser;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import java.util.List;
import java.util.TreeMap;

import javax.measure.Unit;

/**
    Base class of the abstract syntax tree (AST) hierarchy for our expression language.
    To simplify the automatic generation of a parser, this AST is kept separate from the
    node classes created by JavaCC.
**/
public class Operator implements Cloneable
{
    public Object  parent; // The AST node that contains this one. If null or Variable, then this is the root node.
    public Unit<?> unit;   // Stands in for the physical dimensions associated with the output of this operator.

    // Fixed-point
    public static int UNKNOWN = Integer.MIN_VALUE;
    /**
        Zero-based index of most significant bit in the machine word.
        Generally, the bit immediately below the sign bit.
    **/
    public static int MSB = 30;
    /**
        The power of bit that occupies the MSB position, before any shift to prepare value for use by the next operator.
        In the fixed-point analysis implemented by Operator, all bits are fractional just like IEEE floats,
        and we keep track of the power of the most significant bit, just like the IEEE float exponent.
        In the case of a simple integer, exponent is equal to MSB.
        We expect that the fixed-point implementation will do saturation checks, so we don't accommodate
        the largest possible output of each operation, only the median.
        Ideally, only the lower half of the available bits will be occupied.
    **/
    public int exponent = UNKNOWN;
    /**
        Zero-based index of median magnitude.
        For numbers with a range of magnitudes, this will generally be MSB/2 (equivalent to Q16.15 format).
        It is expected that about half the time, all nonzero bits are at or below this position in the word.
        Simple integers, on the other hand, will set this to 0, and all their nonzero bits are at or above this position.
    **/
    public int center = MSB / 2;
    /**
        The power of bit that occupies the MSB position, as required by the operator that contains us.
        Used to determine shifts.
    **/
    public int exponentNext = UNKNOWN;

    public interface Factory extends ExtensionPoint
    {
        public String   name ();  ///< Unique string for searching in the table of registered operators. Used explicitly by parser.
        public Operator createInstance ();
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

    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
    }

    public Operator deepCopy ()
    {
        try
        {
            return (Operator) this.clone ();
        }
        catch (CloneNotSupportedException e)
        {
            return null;
        }
    }

    public enum Associativity
    {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
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
    public boolean isOutput ()
    {
        return false;
    }

    public void visit (Visitor visitor)
    {
        visitor.visit (this);
    }

    public Operator transform (Transformer transformer)
    {
        Operator result = transformer.transform (this);
        if (result != null) return result;
        return this;
    }

    /**
        Remove operators that have no effect due to specific values of their operands (for example: x*1).
        Replaces constant expressions (including any AccessVariable that points to a Constant) with a single Constant.
        Note: a Transformer could do this work, but a direct implementation is more elegant.
        @param from The Variable that contains the current expression. Used, in conjunction with Variable.visited, to
        prevent infinite recursion. It is safe to pass a value of null, since this terminates recursion check.
    **/
    public Operator simplify (Variable from)
    {
        return this;
    }

    public void determineExponent (Variable from)
    {
    }

    public void determineExponentNext (Variable from)
    {
    }

    public void dumpExponents (String pad)
    {
        System.out.print (pad + this + " (");
        if (exponentNext != UNKNOWN) System.out.print (exponentNext + ",");
        if (exponent == UNKNOWN) System.out.print ("--");
        else                     System.out.print (exponent + "," + center);
        System.out.println (")");
    }

    /**
        Utility routine for determineExponent(Variable).
    **/
    public void updateExponent (Variable from, int exponentNew, int centerNew)
    {
        if (exponentNew != exponent  ||  centerNew != center) from.changed = true;
        exponent = exponentNew;
        center   = centerNew;
    }

    /**
        The hint is expected to be a median magnitude, that is, a center power.
        A reasonable default is 0, which produces Q16.16 numbers.
    **/
    public int getExponentHint (String mode, int defaultValue)
    {
        for (String p : mode.split (","))
        {
            if (p.startsWith ("median"))
            {
                String magnitude = "";
                String[] pieces = p.split ("=", 2);
                if (pieces.length > 1) magnitude = pieces[1].trim ();
                if (magnitude.isEmpty ()) return 0;
                try
                {
                    double value = parse (magnitude).getDouble ();
                    if (value <= 0) return 0;
                    return (int) Math.floor (Math.log (value) / Math.log (2));  // log base 2
                }
                catch (ParseException e) {}
                break;
            }
        }
        return defaultValue;
    }

    public int centerPower ()
    {
        return exponent - MSB + center;
    }

    /**
        Sets the unit field, guided by operands and other member data.
        Before this function is called for the first time, unit may be null.
        @param fatal true if mismatched dimensions should produce an exception.
        false if a mismatch should merely result in continued processing with a best guess.
    **/
    public void determineUnit (boolean fatal) throws Exception
    {
    }

    /**
        Remove the dependency of "from" on each variable accessed within the expression tree. 
    **/
    public void releaseDependencies (final Variable from)
    {
        visit (new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    from.removeDependencyOn (av.reference.variable);
                    return false;
                }
                return true;
            }
        });
    }

    public String render ()
    {
        Renderer renderer = new Renderer ();
        render (renderer);
        return renderer.result.toString ();
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        renderer.result.append (toString ());
    }

    public Type getType ()
    {
        throw new EvaluationException ("getType() not implemented.");
    }

    public Type eval (Instance context) throws EvaluationException
    {
        // If an external simulator provides a function, then its backend must provide the
        // internal simulator with an equivalent capability. Thus, generic functions are
        // not allowed. Every legitimate function must be defined by an extension.
        throw new EvaluationException ("Operator not implemented.");
    }

    /**
        Extract the value of a string constant without using eval().
        If this is not a constant, then return "".
        If this is not a string, then return the string equivalent of the constant.
    **/
    public String getString ()
    {
        if (! (this instanceof Constant)) return "";
        return ((Constant) this).value.toString ();
    }

    /**
        Extracts the value of a scalar constant without using eval().
        If this is not a scalar constant, then return 0.
    **/
    public double getDouble ()
    {
        if (! (this instanceof Constant)) return 0;
        Type value = ((Constant) this).value;
        if (value instanceof Scalar) return ((Scalar) value).value;
        return 0;
    }

    /**
        Determines if this is a scalar constant without using eval().
    **/
    public boolean isScalar ()
    {
        if (! (this instanceof Constant)) return false;
        return ((Constant) this).value instanceof Scalar;
    }

    public void solve (Equality statement) throws EvaluationException
    {
        throw new EvaluationException ("Can't solve for this operator.");
    }

    /**
        Utility function to determine whether this operator tree contains the given object.
    **/
    public boolean contains (Operator target)
    {
        class ContainsVisitor extends Visitor
        {
            public boolean found;
            public boolean visit (Operator op)
            {
                if (op == target)
                {
                    found = true;
                    return false;
                }
                return true;
            }
        }
        ContainsVisitor cv = new ContainsVisitor ();
        visit (cv);
        return cv.found;
    }

    public String toString ()
    {
        return "unknown";
    }


    // Static interface ------------------------------------------------------

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
        register (DrawDisc     .factory ());
        register (DrawSegment  .factory ());
        register (Event        .factory ());
        register (Exp          .factory ());
        register (Gaussian     .factory ());
        register (Floor        .factory ());
        register (Grid         .factory ());
        register (Input        .factory ());
        register (Log          .factory ());
        register (Max          .factory ());
        register (Min          .factory ());
        register (Norm         .factory ());
        register (ReadMatrix   .factory ());
        register (Round        .factory ());
        register (Signum       .factory ());
        register (Sine         .factory ());
        register (SquareRoot   .factory ());
        register (Tangent      .factory ());
        register (Output       .factory ());
        register (Uniform      .factory ());
        operators.put ("pow", Power.factory ());  // hack to map pow() function to ^ operator

        // Operators
        register (Add                .factory ());
        register (AND                .factory ());
        register (Divide             .factory ());
        register (EQ                 .factory ());
        register (GE                 .factory ());
        register (GT                 .factory ());
        register (LE                 .factory ());
        register (LT                 .factory ());
        register (Modulo             .factory ());
        register (Multiply           .factory ());
        register (MultiplyElementwise.factory ());
        register (NE                 .factory ());
        register (Negate             .factory ());
        register (NOT                .factory ());
        register (OR                 .factory ());
        register (Power              .factory ());
        register (Subtract           .factory ());
        register (Transpose          .factory ());
    }

    public static void initFromPlugins ()
    {
        List<ExtensionPoint> extensions = PluginManager.getExtensionsForPoint (Factory.class);
        for (ExtensionPoint e : extensions) register ((Factory) e);
    }

    public static Operator parse (String line) throws ParseException
    {
        return getFrom (ExpressionParser.parse (line));
    }

    public static Operator getFrom (SimpleNode node) throws ParseException
    {
        Operator result;
        if (node instanceof ASTOperator)
        {
            Factory f = operators.get (node.jjtGetValue ().toString ());
            result = f.createInstance ();
        }
        else if (node instanceof ASTIdentifier)
        {
            String value = node.jjtGetValue ().toString ();
            if (value.endsWith ("()"))
            {
                Factory f = operators.get (value.substring (0, value.length () - 2));
                if (f == null) result = new AccessElement ();  // It's either this or an undefined function. In the second case, variable access will fail.
                else           result = f.createInstance ();
            }
            else
            {
                result = new AccessVariable ();
            }
        }
        else if (node instanceof ASTConstant) result = new Constant ();
        else if (node instanceof ASTMatrix  ) result = new BuildMatrix ();
        else if (node instanceof ASTList)
        {
            if (node.jjtGetNumChildren () == 1) return getFrom ((SimpleNode) node.jjtGetChild (0));
            result = new Split ();  // Lists can exist elsewhere besides a $type split, but they should be processed out by getOperandsFrom(SimpleNode).
        }
        else result = new Operator ();
        result.getOperandsFrom (node);
        return result;
    }

    public static boolean containsConnect (String line)
    {
        return line.matches ("(^|.*[^\\p{Alnum}$_]+)connect\\s*\\(.*");
    }
}
