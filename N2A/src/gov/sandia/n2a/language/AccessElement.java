/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
        if (node.jjtGetNumChildren () != 1) throw new Error ("AST for function has unexpected form");
        Object o = node.jjtGetChild (0);
        if (! (o instanceof ASTList)) throw new Error ("AST for function has unexpected form");
        ASTList l = (ASTList) o;
        int count = l.jjtGetNumChildren ();

        operands = new Operator[count+1];
        operands[0] = new AccessVariable (node.jjtGetValue ().toString ());
        for (int i = 0; i < count; i++)
        {
            operands[i+1] = Operator.getFrom ((SimpleNode) l.jjtGetChild (i));
        }
    }

    public Operator simplify (Variable from)
    {
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].simplify (from);
        if (operands.length == 1) return operands[0];
        // Implicitly, the number of operands is > 1
        int row = 0;
        int col = 0;
        if (operands[1] instanceof Constant)
        {
            Constant c = (Constant) operands[1];
            if (c.value instanceof Scalar) row = (int) ((Scalar) c.value).value;
        }
        if (operands.length > 2  &&  operands[2] instanceof Constant)
        {
            Constant c = (Constant) operands[2];
            if (c.value instanceof Scalar) col = (int) ((Scalar) c.value).value;
        }
        if (operands[0] instanceof Constant)
        {
            Constant c = (Constant) operands[0];
            if (c.value instanceof Matrix) return new Constant (new Scalar (((Matrix) c.value).getDouble (row, col)));
        }
        else
        {
            // Try to unpack the target variable and see if the specific element we want is constant
            AccessVariable av = (AccessVariable) operands[0];
            if (av.reference != null  &&  av.reference.variable != null)
            {
                Variable v = av.reference.variable;
                if (v.equations != null  &&  v.equations.size () == 1)
                {
                    EquationEntry e = v.equations.first ();
                    if (e.expression instanceof BuildMatrix)
                    {
                        BuildMatrix b = (BuildMatrix) e.expression;
                        Operator element = b.getElement (row, col);
                        if (element != null  &&  element instanceof Constant) return element;
                    }
                }
            }
        }
        return this;
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

    public Type eval (Instance instance)
    {
        Matrix A = (Matrix) operands[0].eval (instance);
        int row = (int) ((Scalar) operands[1].eval (instance)).value;
        int column = 0;
        if (operands.length > 2) column = (int) ((Scalar) operands[2].eval (instance)).value;
        return A.getScalar (row, column);
    }
}
