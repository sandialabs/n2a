/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import tech.units.indriya.AbstractUnit;

public class Power extends OperatorBinary
{
    public String hint;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "^";
            }

            public Operator createInstance ()
            {
                return new Power ();
            }
        };
    }

    public void getOperandsFrom (SimpleNode node) throws Exception
    {
        if (node.jjtGetNumChildren () == 1)
        {
            Object o = node.jjtGetChild (0);
            if (! (o instanceof ASTList)) throw new Error ("AST for function has unexpected form");
            node = (SimpleNode) o;
            hint = "";  // This is in function form, so force all processing to be pow()
        }
        int count = node.jjtGetNumChildren ();
        if (count >= 2)
        {
            operand0 = Operator.getFrom ((SimpleNode) node.jjtGetChild (0));
            operand1 = Operator.getFrom ((SimpleNode) node.jjtGetChild (1));
            if (count > 2)       hint = ((SimpleNode) node.jjtGetChild (2)).jjtGetValue ().toString ();
            operand0.parent = this;
            operand1.parent = this;
        }
        else
        {
            throw new Error ("AST for function has unexpected form");
        }
    }

    public Associativity associativity ()
    {
        if (hint == null) return Associativity.RIGHT_TO_LEFT;  // for ^
        return Associativity.LEFT_TO_RIGHT;  // for pow()
    }

    public int precedence ()
    {
        if (hint == null) return 2;  // for ^
        return 1;  // for pow()
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result != this) return result;

        // Cases we cans simplify:
        // 1^b = 1
        // a^0 = 1
        // a^1 = a
        double c0 = -1;
        double c1 = -1;
        if (operand0 instanceof Constant) c0 = operand0.getDouble ();
        if (operand1 instanceof Constant) c1 = operand1.getDouble ();
        if (c0 == 1  ||  c1 == 0)
        {
            from.changed = true;
            result = new Constant (1);
            result.parent = parent;
            return result;
        }
        if (c1 == 1)
        {
            from.changed = true;
            operand0.parent = parent;
            return operand0;
        }

        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        operand0.determineExponent (context);
        operand1.determineExponent (context);

        // This operator is b^a, where b is the base and a is the power.
        // let p = base 2 power of our result
        // p = log2(b^a) = a*log2(b)
        // See notes on Exp.determineExponent()
        // If the second operand is negative, the user must specify a hint, which requires the use of pow() instead.

        int centerNew   = MSB / 2;
        int exponentNew = UNKNOWN;
        if (operand0.exponent != UNKNOWN  &&  operand1.exponent != UNKNOWN)
        {
            double log2b = 0;
            if (operand0 instanceof Constant)
            {
                double b = operand0.getDouble ();
                if (b != 0) log2b = Math.log (b) / Math.log (2);
            }
            else
            {
                log2b = operand0.centerPower ();
            }

            double a;
            if (operand1 instanceof Constant) a = operand1.getDouble ();
            else                              a = Math.pow (2, operand1.centerPower ());

            exponentNew = 0;
            if (log2b != 0  &&  a != 0) exponentNew = (int) Math.floor (a * log2b);
        }
        if (hint != null) exponentNew = getExponentHint (hint, exponentNew);
        if (exponentNew != UNKNOWN)
        {
            exponentNew += MSB - centerNew;
            updateExponent (context, exponentNew, centerNew);
        }
    }

    public void determineExponentNext ()
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = MSB / 2;  // Exponentiation is very sensitive, so no benefit in allowing arbitrary size of input.
        operand0.determineExponentNext ();
        operand1.determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operand0.determineUnit (fatal);
        operand1.determineUnit (fatal);
        unit = operand0.unit;
        if (unit != null  &&  ! unit.isCompatible (AbstractUnit.ONE)  &&  operand1.isScalar ())
        {
            double value = operand1.getDouble ();
            int b = (int) value;
            if (b == value) unit = UnitValue.simplify (operand0.unit.pow (b));
            else            unit = null;  // TODO: would it make more sense to set unit=ONE ?
            // TODO: handle b=1/i, where i is an integer. User Unit.root()
        }
    }

    public void render (Renderer renderer)
    {
        if (hint == null)  // render as ^
        {
            super.render (renderer);
        }
        else  // render as pow()
        {
            if (renderer.render (this)) return;
            renderer.result.append ("pow(");
            operand0.render (renderer);
            renderer.result.append (", ");
            operand1.render (renderer);
            if (! hint.isEmpty ()) renderer.result.append (", \"" + hint + "\"");
            renderer.result.append (")");
        }
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).power (operand1.eval (context));
    }

    public Operator inverse (Operator lhs, Operator rhs)
    {
        if (lhs == operand1)
        {
            Divide inv = new Divide ();
            inv.operand0 = new Constant (1);
            inv.operand1 = lhs;
            Power result = new Power ();
            result.operand0 = rhs;
            result.operand1 = inv;
            return result;
        }

        Log log = new Log ();
        log.operands[0] = lhs;
        Divide result = new Divide ();
        result.operand0 = rhs;
        result.operand1 = log;
        return result;
    }

    public String toString ()
    {
        if (hint == null) return "^";
        return "pow";
    }
}
