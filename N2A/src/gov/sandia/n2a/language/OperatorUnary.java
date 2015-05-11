/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTNodeBase;
import gov.sandia.n2a.language.parse.ParseException;

public class OperatorUnary extends Operator
{
    public Operator operand;

    public void getOperandsFrom (ASTNodeBase node) throws ParseException
    {
        if (node.jjtGetNumChildren () != 1) throw new ParseException ("AST for operator has unexpected form");
        operand = Operator.getFrom ((ASTNodeBase) node.jjtGetChild (0));
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
        if (operand instanceof Constant) return new Constant (eval (null));
        return this;
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        renderer.result.append (toString ());
        operand.render (renderer);
    }
}
