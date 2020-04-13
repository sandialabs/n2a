/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.SimpleNode;
import tech.units.indriya.AbstractUnit;

public class OperatorBinary extends Operator implements OperatorArithmetic
{
    public Operator operand0;
    public Operator operand1;
    protected Type type;

    public void getOperandsFrom (SimpleNode node) throws Exception
    {
        if (node.jjtGetNumChildren () != 2) throw new Error ("AST for operator has unexpected form");
        operand0 = Operator.getFrom ((SimpleNode) node.jjtGetChild (0));
        operand1 = Operator.getFrom ((SimpleNode) node.jjtGetChild (1));
        operand0.parent = this;
        operand1.parent = this;
    }

    public Operator deepCopy ()
    {
        OperatorBinary result = null;
        try
        {
            result = (OperatorBinary) this.clone ();
            result.operand0 = operand0.deepCopy ();
            result.operand1 = operand1.deepCopy ();
            result.operand0.parent = result;
            result.operand1.parent = result;
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
        if (operand0 instanceof Constant  &&  operand1 instanceof Constant)
        {
            from.changed = true;
            Operator result = new Constant (eval (null));
            result.parent = parent;
            return result;
        }
        return this;
    }

    /**
        Adjusts a constant operand so it better aligns with the non-constant operand.
        Directly modifies the fixed-point configuration of the constant.
    **/
    public void alignExponent (Variable from)
    {
        if      (operand0.isScalar ()) ((Constant) operand0).determineExponent (from, operand1.exponent);
        else if (operand1.isScalar ()) ((Constant) operand1).determineExponent (from, operand0.exponent);
    }

    public void determineExponentNext (Variable from)
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponentNext (from);
        operand1.determineExponentNext (from);
    }

    public void dumpExponents (String pad)
    {
        super.dumpExponents (pad);
        operand0.dumpExponents (pad + "  ");
        operand1.dumpExponents (pad + "  ");
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operand0.determineUnit (fatal);
        operand1.determineUnit (fatal);
        unit = operand0.unit;
        if (operand1.unit != null)
        {
            if (unit == null  ||  unit.isCompatible (AbstractUnit.ONE))
            {
                unit = operand1.unit;
            }
            else if (fatal  &&  ! operand1.unit.isCompatible (AbstractUnit.ONE)  &&  ! operand1.unit.isCompatible (unit))
            {
                throw new Exception (unit + " " + toString () + " " + operand1.unit);
            }
        }
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        // As a matter of style, add space around logical operators. This is completely arbitrary.
        if (this instanceof OperatorLogical) render (renderer, " " + toString () + " ");
        else                                 render (renderer,       toString ());
    }

    public void render (Renderer renderer, String middle)
    {
        // Left-hand child
        boolean needParens = false;
        if (operand0 instanceof OperatorArithmetic)
        {
            needParens =    precedence () < operand0.precedence ()   // read "<" as "comes before" rather than "less"
                         ||    precedence () == operand0.precedence ()
                            && associativity () == Associativity.RIGHT_TO_LEFT;
        }
        if (needParens) renderer.result.append ("(");
        operand0.render (renderer);
        if (needParens) renderer.result.append (")");

        renderer.result.append (middle);

        // Right-hand child
        needParens = false;
        if (operand1 instanceof OperatorArithmetic)
        {
            needParens =    precedence () < operand1.precedence ()
                         ||    precedence () == operand1.precedence ()
                            && associativity () == Associativity.LEFT_TO_RIGHT;
        }
        if (needParens) renderer.result.append ("(");
        operand1.render (renderer);
        if (needParens) renderer.result.append (")");
    }

    public Type getType ()
    {
        if (type != null) return type;
        Type a = operand0.getType ();
        Type b = operand1.getType ();
        if (a.betterThan (b)) type = a;
        else                  type = b;
        return type;
    }

    public void solve (Equality statement) throws EvaluationException
    {
        if (operand0.contains (statement.target))  // need left-inverse
        {
            statement.rhs = inverse (operand1, statement.rhs);
            statement.lhs = operand0;
        }
        else  // need right-inverse
        {
            statement.rhs = inverse (operand0, statement.rhs);
            statement.lhs = operand1;
        }
    }

    /**
        In an equality statement, moves one of our two operands over to the rhs.
        @param lhs The selected operand. Will be one of operand0 or operand1.
        @param rhs The current rhs from the equality statement.
        @return A new operator to replace the rhs in the equality statement.
    **/
    public Operator inverse (Operator lhs, Operator rhs) throws EvaluationException
    {
        throw new EvaluationException ("Can't invert this operator.");
    }

    public boolean equals (Object that)
    {
        if (! (that instanceof OperatorBinary)) return false;
        OperatorBinary o = (OperatorBinary) that;

        // TODO: For commutative operators, test all possible orderings.
        // Commutative operators may become a proper subclass of OperatorBinary.
        return operand0.equals (o.operand0)  &&  operand1.equals (o.operand1);
    }
}
