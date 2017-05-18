/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.parse.ParseException;

public class Function extends Operator
{
    public Operator[] operands;

    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
        if (node.jjtGetNumChildren () == 0)
        {
            operands = new Operator[0];
            return;
        }
        if (node.jjtGetNumChildren () != 1) throw new ParseException ("AST for function has unexpected form");
        Object o = node.jjtGetChild (0);
        if (! (o instanceof ASTList)) throw new ParseException ("AST for function has unexpected form");
        ASTList l = (ASTList) o;
        int count = l.jjtGetNumChildren ();
        operands = new Operator[count];
        for (int i = 0; i < count; i++) operands[i] = Operator.getFrom ((SimpleNode) l.jjtGetChild (i));
    }

    public Operator deepCopy ()
    {
        Function result = null;
        try
        {
            result = (Function) this.clone ();
            for (int i = 0; i < operands.length; i++) result.operands[i] = operands[i].deepCopy ();
        }
        catch (CloneNotSupportedException e)
        {
        }
        return result;
    }

    public boolean isOutput ()
    {
        for (int i = 0; i < operands.length; i++)
        {
            if (operands[i].isOutput ()) return true;
        }
        return false;
    }

    public void visit (Visitor visitor)
    {
        if (! visitor.visit (this)) return;
        for (int i = 0; i < operands.length; i++) operands[i].visit (visitor);
    }

    public Operator transform (Transformer transformer)
    {
        Operator result = transformer.transform (this);
        if (result != null) return result;
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].transform (transformer);
        return this;
    }

    public Operator simplify (Variable from)
    {
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].simplify (from);
        return this;
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        renderer.result.append (toString () + "(");
        for (int a = 0; a < operands.length; a++)
        {
            operands[a].render (renderer);
            if (a < operands.length - 1) renderer.result.append (", ");
        }
        renderer.result.append (")");
    }

    public int compareTo (Operator that)
    {
        Class<? extends Operator> thisClass = getClass ();
        Class<? extends Operator> thatClass = that.getClass ();
        if (! thisClass.equals (thatClass)) return thisClass.hashCode () - thatClass.hashCode ();

        // Same class as us, so compare operands
        Function f = (Function) that;
        int difference = operands.length - f.operands.length;
        if (difference != 0) return difference;
        for (int i = 0; i < operands.length; i++)
        {
            difference = operands[i].compareTo (f.operands[i]);
            if (difference != 0) return difference;
        }

        return 0;
    }
}
