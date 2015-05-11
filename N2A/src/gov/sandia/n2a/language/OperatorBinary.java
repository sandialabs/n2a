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

public class OperatorBinary extends Operator
{
    public Operator operand0;
    public Operator operand1;

    public void getOperandsFrom (ASTNodeBase node) throws ParseException
    {
        if (node.jjtGetNumChildren () != 2) throw new ParseException ("AST for operator has unexpected form");
        operand0 = Operator.getFrom ((ASTNodeBase) node.jjtGetChild (0));
        operand1 = Operator.getFrom ((ASTNodeBase) node.jjtGetChild (1));
    }

    public Operator deepCopy ()
    {
        OperatorBinary result = null;
        try
        {
            result = (OperatorBinary) this.clone ();
            result.operand0 = operand0.deepCopy ();
            result.operand1 = operand1.deepCopy ();
        }
        catch (CloneNotSupportedException e)
        {
        }
        return result;
    }

    public boolean isOutput ()
    {
        return operand0.isOutput ()  ||  operand1.isOutput ();
    }

    public void visit (Visitor visitor)
    {
        if (! visitor.visit (this)) return;
        operand0.visit (visitor);
        operand1.visit (visitor);
    }

    public Operator transform (Transformer transformer)
    {
        Operator result = transformer.transform (this);
        if (result != null) return result;
        operand0 = operand0.transform (transformer);
        operand1 = operand1.transform (transformer);
        return this;
    }

    public Operator simplify (Variable from)
    {
        operand0 = operand0.simplify (from);
        operand1 = operand1.simplify (from);
        if (operand0 instanceof Constant  &&  operand1 instanceof Constant) return new Constant (eval (null));
        return this;
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;

        // Left-hand child
        boolean useParens = false;
        if (operand0 instanceof OperatorBinary  ||  operand0 instanceof OperatorUnary)
        {
            useParens =    precedence () < operand0.precedence ()   // read "<" as "comes before" rather than "less"
                        ||    precedence () == operand0.precedence ()
                           && associativity () == Associativity.RIGHT_TO_LEFT;
        }
        if (useParens) renderer.result.append ("(");
        operand0.render (renderer);
        if (useParens) renderer.result.append (")");

        renderer.result.append (" " + toString () + " ");

        // Right-hand child
        useParens = false;
        if (operand1 instanceof OperatorBinary  ||  operand1 instanceof OperatorUnary)
        {
            useParens =    precedence () < operand1.precedence ()   // read "<" as "comes before" rather than "less"
                        ||    precedence () == operand1.precedence ()
                           && associativity () == Associativity.LEFT_TO_RIGHT;
        }
        if (useParens) renderer.result.append ("(");
        operand1.render (renderer);
        if (useParens) renderer.result.append (")");
    }
}
