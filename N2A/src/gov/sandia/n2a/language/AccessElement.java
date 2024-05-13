/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.EquationSet.NonzeroIterable;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.function.Mmatrix;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Matrix.IteratorNonzero;
import gov.sandia.n2a.language.type.Scalar;

public class AccessElement extends Function implements NonzeroIterable
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
        operands[0] = new AccessVariable (node);
        operands[0].parent = this;
        for (int i = 0; i < count; i++)
        {
            operands[i+1] = Operator.getFrom ((SimpleNode) l.jjtGetChild (i));
            operands[i+1].parent = this;
        }
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].simplify (from, evalOnly);
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
                            if (! evalOnly) operands[0].releaseDependencies (from);
                            element.parent = parent;
                            return element;
                        }
                    }
                }
            }
        }
        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        Operator v = operands[0];
        v.determineExponent (context);
        updateExponent (context, v.exponent, v.center);

        for (int i = 1; i < operands.length; i++)
        {
            operands[i].determineExponent (context);
        }
    }

    public void determineExponentNext ()
    {
        Operator v = operands[0];
        if (v instanceof Constant  ||  v instanceof AccessVariable)  // The preferred use of this function is to access a non-calculated matrix.
        {
            v.exponentNext = v.exponent;  // Let matrix output in its natural exponent.
            exponent       = v.exponent;  // Force our output to be shifted after the fact.
        }
        else  // A matrix expression of some sort, so pass the required exponent on to it. This case should be rare.
        {
            v.exponentNext = exponentNext;  // Pass the required exponent on to the expression
            exponent       = exponentNext;  // and expect that we require not further adjustment.
        }
        v.determineExponentNext ();

        for (int i = 1; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = 0;  // forces pure integer
            op.determineExponentNext ();
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
        try
        {
            double row = ((Scalar) operands[1].eval (instance)).value;
            double column = 0;
            if (operands.length > 2) column = ((Scalar) operands[2].eval (instance)).value;
            return new Scalar (A.get (row, column, Matrix.INTERPOLATE));  // This access function does bounds check.
        }
        catch (ClassCastException e)
        {
            String message = "Matrix indices must be numbers";
            String v = container ();
            if (! v.isEmpty ()) message += ": " + v;
            throw new EvaluationException (message);
        }
    }

    public Operator operandA ()
    {
        if (operands.length > 1) return operands[1];
        return null;
    }

    public Operator operandB ()
    {
        if (operands.length > 2) return operands[2];
        return null;
    }

    public IteratorNonzero getIteratorNonzero (Instance context)
    {
        Matrix A = (Matrix) operands[0].eval (context);
        return A.getIteratorNonzero ();
    }

    public boolean hasCorrectForm ()
    {
        if (operands.length < 3) return false;  // Must be 2-dimensional access
        Operator op0 = operands[0];
        if (op0 instanceof Constant) return true;
        if (! (op0 instanceof AccessVariable)) return false;  // Some other matrix expression.

        // Simply assume that the variable is of type Matrix.
        // Need to know if it is sufficiently constant.
        AccessVariable av = (AccessVariable) op0;
        Variable v = av.reference.variable;
        if (v.hasAny ("constant", "initOnly")) return true;

        // See if v is just an alias for a matrix reader.
        if (v.equations.size () != 1) return false;
        EquationEntry e = v.equations.first ();
        if (e.condition != null) return false;
        if (e.expression instanceof ReadMatrix)
        {
            ReadMatrix r = (ReadMatrix) e.expression;
            return r.operands[0] instanceof Constant;  // File name must be constant.
        }
        if (e.expression instanceof Mmatrix)
        {
            Mmatrix m = (Mmatrix) e.expression;
            for (Operator op : m.operands) if (! (op instanceof Constant)) return false;
            return true;
        }
        return false;
    }
}
