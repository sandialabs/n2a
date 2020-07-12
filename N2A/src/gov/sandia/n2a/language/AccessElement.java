/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class AccessElement extends Function
{
    public void getOperandsFrom (SimpleNode node) throws Exception
    {
        if (node.jjtGetNumChildren () == 0) throw new UnsupportedFunctionException (node.jjtGetValue ().toString ());
        if (node.jjtGetNumChildren () != 1) throw new Error ("AST for function has unexpected form");
        Object o = node.jjtGetChild (0);
        if (! (o instanceof ASTList)) throw new Error ("AST for function has unexpected form");
        ASTList l = (ASTList) o;
        int count = l.jjtGetNumChildren ();

        operands = new Operator[count+1];
        String value = node.jjtGetValue ().toString ();
        operands[0] = new AccessVariable (value.substring (0, value.length () - 2));  // Remove "()" from the end of name.
        operands[0].parent = this;
        for (int i = 0; i < count; i++)
        {
            operands[i+1] = Operator.getFrom ((SimpleNode) l.jjtGetChild (i));
            operands[i+1].parent = this;
        }
    }

    public Operator simplify (Variable from)
    {
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].simplify (from);
        if (operands.length == 1)
        {
            from.changed = true;
            operands[0].parent = parent;
            return operands[0];
        }

        // All operand positions beyond 0 are subscripts, presumably into a matrix at operands[0].
        // Attempt to replace the element access with a constant.
        int row = -1;
        int col = 0;
        if (operands[1] instanceof Constant)
        {
            Constant c = (Constant) operands[1];
            if (c.value instanceof Scalar) row = (int) ((Scalar) c.value).value;
        }
        if (operands.length > 2)
        {
            col = -1;
            if (operands[2] instanceof Constant)
            {
                Constant c = (Constant) operands[2];
                if (c.value instanceof Scalar) col = (int) ((Scalar) c.value).value;
            }
        }
        if (row < 0  ||  col < 0) return this;
            
        if (operands[0] instanceof Constant)
        {
            Constant c = (Constant) operands[0];
            if (c.value instanceof Matrix)
            {
                from.changed = true;
                Operator result = new Constant (new Scalar (((Matrix) c.value).get (row, col)));
                result.parent = parent;
                return result;
            }
        }
        else  // If not constant (above), then operands[0] should always be an AccessVariable.
        {
            // Try to unpack the target variable and see if the specific element we want is constant
            AccessVariable av = (AccessVariable) operands[0];
            if (av.reference != null  &&  av.reference.variable != null)
            {
                Variable v = av.reference.variable;
                if (v.equations != null  &&  v.equations.size () == 1)
                {
                    EquationEntry e = v.equations.first ();
                    if (e.expression instanceof BuildMatrix  &&  (e.condition == null  ||  e.condition.getDouble () != 0))  // Only weird code would have a condition at all.
                    {
                        BuildMatrix b = (BuildMatrix) e.expression;
                        Operator element = b.getElement (row, col);
                        if (element != null  &&  element instanceof Constant)
                        {
                            from.changed = true;
                            operands[0].releaseDependencies (from);
                            element.parent = parent;
                            return element;
                        }
                    }
                }
            }
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        Operator v = operands[0];
        v.determineExponent (from);
        updateExponent (from, v.exponent, v.center);

        for (int i = 1; i < operands.length; i++)
        {
            operands[i].determineExponent (from);
        }
    }

    public void determineExponentNext (Variable from)
    {
        Operator v = operands[0];
        v.exponentNext = exponentNext;
        v.determineExponentNext (from);

        for (int i = 1; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = MSB;  // forces pure integer
            op.determineExponentNext (from);
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = operands[0].unit;
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        operands[0].render (renderer);  // render variable
        renderer.result.append ("(");
        if (operands.length > 1)
        {
            operands[1].render (renderer);  // first subscript
            if (operands.length > 2)
            {
                renderer.result.append (", ");
                operands[2].render (renderer);  // second subscript
            }
        }
        renderer.result.append (")");
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance instance)
    {
        Matrix A = (Matrix) operands[0].eval (instance);
        int row = (int) ((Scalar) operands[1].eval (instance)).value;
        int column = 0;
        if (operands.length > 2) column = (int) ((Scalar) operands[2].eval (instance)).value;
        return new Scalar (A.get (row, column));
    }
}
