/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTKeyword;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import tech.units.indriya.AbstractUnit;

public class Function extends Operator
{
    public Operator[]           operands;  // always non-null, even if there are no positional parameters
    public Map<String,Operator> keywords;  // null if there are no keywords

    public void getOperandsFrom (SimpleNode node) throws Exception
    {
        if (node.jjtGetNumChildren () == 0)
        {
            operands = new Operator[0];
            return;
        }
        ASTList list = (ASTList) node.jjtGetChild (0);
        int count = list.jjtGetNumChildren ();
        List<Operator> positional = new ArrayList<Operator> ();
        for (int i = 0; i < count; i++)
        {
            SimpleNode n = (SimpleNode) list.jjtGetChild (i);
            if (n instanceof ASTKeyword)
            {
                Operator op   = Operator.getFrom ((SimpleNode) n.jjtGetChild (0));
                String   name = n.jjtGetValue ().toString ();
                addKeyword (name, op);
            }
            else
            {
                Operator op = Operator.getFrom (n);
                op.parent = this;
                positional.add (op);
            }
        }
        operands = positional.toArray (new Operator[positional.size ()]);
    }

    public void addKeyword (String name, Operator op)
    {
        op.parent = this;
        if (keywords == null) keywords = new HashMap<String,Operator> ();
        keywords.put (name, op);
    }

    public Operator getKeyword (String name)
    {
        if (keywords == null) return null;
        return keywords.get (name);
    }

    public boolean getKeywordFlag (String name)
    {
        Operator keyword = getKeyword (name);
        if (keyword == null) return false;
        return keyword.getDouble () != 0;
    }

    public Type evalKeyword (Instance context, String name)
    {
        Operator keyword = getKeyword (name);
        if (keyword == null) return null;
        return keyword.eval (context);
    }

    public String evalKeyword (Instance context, String name, String defaultValue)
    {
        Type result = evalKeyword (context, name);
        if (result == null) return defaultValue;
        return result.toString ();
    }

    public boolean evalKeywordFlag (Instance context, String name)
    {
        Type result = evalKeyword (context, name);
        if (result == null) return false;
        if (result instanceof Scalar) return ((Scalar) result).value != 0;
        return true;
    }

    public boolean evalKeyword (Instance context, String name, boolean defaultValue)
    {
        Type result = evalKeyword (context, name);
        if (result == null) return defaultValue;
        if (result instanceof Scalar) return ((Scalar) result).value != 0;
        if (result instanceof Text)
        {
            String value = ((Text) result).value;
            if (value.equals ("1")) return true;
            if (value.equalsIgnoreCase ("true")) return true;
            return false;
        }
        if (result instanceof Matrix) return true;  // Treat Matrix as always true.
        return defaultValue;
    }

    public int evalKeyword (Instance context, String name, int defaultValue)
    {
        Type result = evalKeyword (context, name);
        if (result == null) return defaultValue;
        if (result instanceof Scalar) return (int) Math.round (((Scalar) result).value);
        if (result instanceof Text)
        {
            String value = ((Text) result).value;
            try
            {
                return Integer.valueOf (value);
            }
            catch (NumberFormatException e)
            {
                return defaultValue;
            }
        }
        if (result instanceof Matrix)
        {
            Matrix m = (Matrix) result;
            if (m.rows () >= 1  &&  m.columns () >= 1) return (int) Math.round (m.get (0, 0));
        }
        return defaultValue;
    }

    public long evalKeyword (Instance context, String name, long defaultValue)
    {
        Type result = evalKeyword (context, name);
        if (result == null) return defaultValue;
        if (result instanceof Scalar) return Math.round (((Scalar) result).value);
        if (result instanceof Text)
        {
            String value = ((Text) result).value;
            try
            {
                return Long.valueOf (value);
            }
            catch (NumberFormatException e)
            {
                return defaultValue;
            }
        }
        if (result instanceof Matrix)
        {
            Matrix m = (Matrix) result;
            if (m.rows () >= 1  &&  m.columns () >= 1) return Math.round (m.get (0, 0));
        }
        return defaultValue;
    }

    public double evalKeyword (Instance context, String name, double defaultValue)
    {
        Type result = evalKeyword (context, name);
        if (result == null) return defaultValue;
        if (result instanceof Scalar) return ((Scalar) result).value;
        if (result instanceof Text)
        {
            String value = ((Text) result).value;
            try
            {
                return Double.valueOf (value);
            }
            catch (NumberFormatException e)
            {
                return defaultValue;
            }
        }
        if (result instanceof Matrix)
        {
            Matrix m = (Matrix) result;
            if (m.rows () >= 1  &&  m.columns () >= 1) return m.get (0, 0);
        }
        return defaultValue;
    }

    public Color evalKeyword (Instance context, String name, Color defaultValue)
    {
        Type result = evalKeyword (context, name);
        if (result instanceof Matrix)
        {
            Matrix C = (Matrix) result;
            int count = C.rows ();
            if (count == 1) count = C.columns ();
            float r = (float) C.get (0);  // Assumes that C is backed by MatrixDense, where this will work for both vertical or horizontal orientation.
            float g = (float) C.get (1);
            float b = (float) C.get (2);
            float a = 1;
            if (count > 3) a = (float) C.get (3);
            return new Color (r, g, b, a);
        }
        if (result instanceof Scalar)
        {
            int value = (int) ((Scalar) result).value;
            return new Color (value);  // no alpha channel allowed
        }
        if (result instanceof Text)
        {
            String value = result.toString ();
            if (! value.isBlank ())
            {
                try {return Color.decode (value);}
                catch (NumberFormatException e) {}
            }
        }
        return defaultValue;
    }

    public Operator deepCopy ()
    {
        Function result = null;
        try
        {
            result = (Function) this.clone ();
            Operator[] newOperands = new Operator[operands.length];
            for (int i = 0; i < operands.length; i++)
            {
                newOperands[i] = operands[i].deepCopy ();
                newOperands[i].parent = result;
            }
            result.operands = newOperands;

            if (keywords != null)
            {
                result.keywords = new HashMap<String,Operator> ();
                for (Entry<String,Operator> k : keywords.entrySet ())
                {
                    Operator op = k.getValue ().deepCopy ();
                    op.parent = result;
                    result.keywords.put (k.getKey (), op);
                }
            }
        }
        catch (CloneNotSupportedException e)
        {
        }
        return result;
    }

    public boolean isOutput ()
    {
        for (Operator op : operands)
        {
            if (op.isOutput ()) return true;
        }
        if (keywords == null) return false;
        for (Operator op : keywords.values ())
        {
            if (op.isOutput ()) return true;
        }
        return false;
    }

    /**
        Indicates that when all parameters of this function are constant, its value can be replaced by a constant at compile time.
        Most basic arithmetic functions fall in this category. Random number generators, inputs and outputs do not.
    **/
    public boolean canBeConstant ()
    {
        return true;
    }

    /**
        Indicates that when all parameters of this function are known during the init cycle and do not change after that,
        this function only needs to be evaluated once.
    **/
    public boolean canBeInitOnly ()
    {
        return canBeConstant ();
    }

    public void visit (Visitor visitor)
    {
        if (! visitor.visit (this)) return;
        for (Operator op : operands) op.visit (visitor);
        if (keywords == null) return;
        for (Operator op : keywords.values ()) op.visit (visitor);
    }

    public Operator transform (Transformer transformer)
    {
        Operator result = transformer.transform (this);
        if (result != null) return result;
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].transform (transformer);
        if (keywords == null) return this;
        for (Entry<String,Operator> k : keywords.entrySet ()) k.setValue (k.getValue ().transform (transformer));
        return this;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        boolean allConstant = true;
        for (int i = 0; i < operands.length; i++)
        {
            operands[i] = operands[i].simplify (from, evalOnly);
            if (! (operands[i] instanceof Constant)) allConstant = false;
        }
        if (keywords != null)
        {
            for (Entry<String,Operator> k : keywords.entrySet ())
            {
                Operator op = k.getValue ().simplify (from, evalOnly);
                k.setValue (op);
                if (! (op instanceof Constant)) allConstant = false;
            }
        }
        if (allConstant  &&  canBeConstant ())  // This function can be replaced by a constant.
        {
            from.changed = true;
            Operator result = new Constant (eval (null));  // A function should report canBeConstant() true only if null is safe to pass here.
            result.parent = parent;
            return result;
        }
        return this;
    }

    /**
        Finds the average exponent and center of our inputs, based on the assumption
        that our output naturally matches our inputs.
    **/
    public void determineExponent (ExponentContext context)
    {
        int cent  = 0;
        int pow   = 0;
        int count = 0;
        for (Operator op : operands)
        {
            op.determineExponent (context);
            if (op.exponent != UNKNOWN)
            {
                cent += op.center;
                pow  += op.exponent;
                count++;
            }
        }
        if (count > 0)
        {
            cent /= count;
            pow  /= count;
            updateExponent (context, pow, cent);
        }

        // Keywords don't factor into function's overall exponent
        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.determineExponent (context);
        }
    }

    /**
        Passes our required output exponent on to the inputs, and assumes that
        that we will naturally output the same exponent as our inputs.
    **/
    public void determineExponentNext ()
    {
        exponent = exponentNext;  // Assumes that we simply output the same exponent as our inputs.
        for (Operator op : operands)
        {
            op.exponentNext = exponentNext;  // Passes the required exponent down to operands.
            op.determineExponentNext ();
        }

        // Keyword exponentNext is very function-specific. Should be overridden by child classes.
        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.exponentNext = op.exponent;
            op.determineExponentNext ();
        }
    }

    public void dumpExponents (String pad)
    {
        super.dumpExponents (pad);
        for (Operator op : operands) op.dumpExponents (pad + "  ");

        if (keywords == null) return;
        for (Operator op : keywords.values ()) op.dumpExponents (pad + "  ");
    }

    /**
        The hint is expected to be a median magnitude, that is, a center power.
        A reasonable default is 0, which produces Q16.16 numbers.
    **/
    public int getExponentHint (int defaultValue)
    {
        if (keywords == null) return defaultValue;
        Operator median = keywords.get ("median");
        if (median == null) return defaultValue;

        double value = median.getDouble ();
        if (value <= 0) return 0;
        return (int) Math.floor (Math.log (value) / Math.log (2));  // log base 2
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = null;
        for (Operator op : operands)
        {
            op.determineUnit (fatal);
            if (op.unit != null)
            {
                if (unit == null  ||  unit.isCompatible (AbstractUnit.ONE))
                {
                    unit = op.unit;
                }
                else if (fatal  &&  ! op.unit.isCompatible (AbstractUnit.ONE)  &&  ! op.unit.isCompatible (unit))
                {
                    throw new Exception (toString () + "(" + unit + " versus " + op.unit + ")");
                }
            }
        }

        // Keyword arguments have no effect on function's overall unit.
        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.determineUnit (fatal);
        }
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        renderer.result.append (toString () + "(");
        if (operands.length > 0)
        {
            operands[0].render (renderer);
            for (int i = 1; i < operands.length; i++)
            {
                renderer.result.append (", ");
                operands[i].render (renderer);
            }
        }
        if (keywords != null)
        {
            boolean needComma = operands.length > 0;
            for (Entry<String,Operator> k : keywords.entrySet ())
            {
                if (needComma) renderer.result.append (", " + k.getKey () + "=");
                k.getValue ().render (renderer);
                needComma = true;
            }
        }
        renderer.result.append (")");
    }

    public Type getType ()
    {
        if (operands == null  ||  operands.length == 0) return new Scalar ();
        return operands[0].getType ();
    }

    public boolean equals (Object that)
    {
        if (! (that instanceof Function)) return false;
        Function f = (Function) that;

        if (operands.length != f.operands.length) return false;
        for (int i = 0; i < operands.length; i++)
        {
            if (! operands[i].equals (f.operands[i])) return false;
        }

        if (keywords != null  &&  f.keywords != null) return keywords.equals (f.keywords);
        return  keywords == null  &&  f.keywords == null;
    }
}
