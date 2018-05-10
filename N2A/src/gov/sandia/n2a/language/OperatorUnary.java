/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.SimpleNode;

public class OperatorUnary extends Operator
{
    public Operator operand;

    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
        if (node.jjtGetNumChildren () != 1) throw new Error ("AST for operator has unexpected form");
        operand = Operator.getFrom ((SimpleNode) node.jjtGetChild (0));
    }

    public Operator deepCopy ()
    {
        OperatorUnary result = null;
        try
        {
            result = (OperatorUnary) this.clone ();
            result.operand = operand.deepCopy ();
        }
        catch (CloneNotSupportedException e)
        {
        }
        return result;
    }

    public boolean isOutput ()
    {
        return operand.isOutput ();
    }

    public void visit (Visitor visitor)
    {
        if (! visitor.visit (this)) return;
        operand.visit (visitor);
    }

    public Operator transform (Transformer transformer)
    {
        Operator result = transformer.transform (this);
        if (result != null) return result;
        operand = operand.transform (transformer);
        return this;
    }

    public Operator simplify (Variable from)
    {
        operand = operand.simplify (from);
        if (operand instanceof Constant)
        {
            from.changed = true;
            return new Constant (eval (null));
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand.exponentNext = exponentNext;
        operand.determineExponent (from);
        updateExponent (from, operand.exponent);
    }

    public void dumpExponents (String pad)
    {
        //System.out.println (pad + this + " " + exponentNext + " " + exponent);
        System.out.println (pad + this + " " + exponent);
        operand.dumpExponents (pad + "  ");
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        renderer.result.append (toString ());
        operand.render (renderer);
    }

    public boolean equals (Object that)
    {
        if (! (that instanceof OperatorUnary)) return false;
        OperatorUnary o = (OperatorUnary) that;
        return operand.equals (o.operand);
    }
}
