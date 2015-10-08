/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTConstant;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.parse.ParseException;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class BuildMatrix extends Operator
{
    public Operator[][] operands;  // stored in column-major order; that is, access as operands[column][row]

    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
        int rows = node.jjtGetNumChildren ();
        int cols = 0;
        for (int r = 0; r < rows; r++)
        {
            SimpleNode row = (SimpleNode) node.jjtGetChild (r);
            int c = 1;
            if (! (row instanceof ASTConstant)) c = row.jjtGetNumChildren ();
            cols = Math.max (cols, c);
        }

        operands = new Operator[cols][rows];
        for (int r = 0; r < rows; r++)
        {
            SimpleNode row = (SimpleNode) node.jjtGetChild (r);
            if (row instanceof ASTConstant)
            {
                operands[0][r] = Operator.getFrom (row);
            }
            else
            {
                int currentCols = row.jjtGetNumChildren ();
                for (int c = 0; c < currentCols; c++)
                {
                    operands[c][r] = Operator.getFrom ((SimpleNode) row.jjtGetChild (c));
                }
            }
        }
    }

    public Operator deepCopy ()
    {
        BuildMatrix result = null;
        try
        {
            result = (BuildMatrix) this.clone ();
            int columns = operands.length;
            if (columns == 0) return result;
            int rows = operands[0].length;
            for (int c = 0; c < columns; c++)
            {
                for (int r = 0; r < rows; r++)
                {
                    if (operands[c][r] != null) result.operands[c][r] = operands[c][r].deepCopy ();
                }
            }
        }
        catch (CloneNotSupportedException e)
        {
        }
        return result;
    }

    public int getRows ()
    {
        if (operands.length < 1) return 0;
        return operands[0].length;
    }

    public int getColumns ()
    {
        return operands.length;
    }

    public Operator getElement (int row, int column)
    {
        if (operands.length         <= column) return null;
        if (operands[column].length <= row   ) return null;
        return operands[column][row];
    }

    public void visit (Visitor visitor)
    {
        if (! visitor.visit (this)) return;

        int columns = operands.length;
        if (columns == 0) return;
        int rows = operands[0].length;
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] != null) operands[c][r].visit (visitor);
            }
        }
    }

    public Operator transform (Transformer transformer)
    {
        Operator result = transformer.transform (this);
        if (result != null) return result;

        int columns = operands.length;
        if (columns == 0) return this;
        int rows = operands[0].length;
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] != null) operands[c][r] = operands[c][r].transform (transformer);
            }
        }
        
        return this;
    }

    public Operator simplify (Variable from)
    {
        int cols = operands.length;
        if (cols == 0) return this;
        int rows = operands[0].length;
        if (rows == 0) return this;

        Matrix A = new Matrix (rows, cols);  // potential constant to replace us
        boolean isConstant = true;  // any element that is not constant will change this to false
        for (int c = 0; c < cols; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] == null)
                {
                    A.value[c][r] = 0;
                }
                else
                {
                    operands[c][r] = operands[c][r].simplify (from);
                    if (isConstant)  // stop evaluating if we already know we are not constant
                    {
                        if (operands[c][r] instanceof Constant)
                        {
                            Type o = ((Constant) operands[c][r]).value;
                            if      (o instanceof Scalar) A.value[c][r] = ((Scalar) o).value;
                            else if (o instanceof Text  ) A.value[c][r] = Double.valueOf (((Text) o).value);
                            else if (o instanceof Matrix) A.value[c][r] = ((Matrix) o).value[0][0];
                            else throw new EvaluationException ("Can't construct matrix element from the given type.");
                        }
                        else
                        {
                            isConstant = false;
                        }
                    }
                }
            }
        }

        if (isConstant) return new Constant (A);
        return this;
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;

        int columns = operands.length;
        if (columns == 0)
        {
            renderer.result.append ("[]");
            return;
        }
        int rows = operands[0].length;
        if (rows == 0)
        {
            renderer.result.append ("[]");
            return;
        }

        renderer.result.append ("[");
        int r = 0;
        while (true)
        {
            int c = 0;
            while (true)
            {
                if (operands[c][r] != null) operands[c][r].render (renderer);
                else                         renderer.result.append ("0");
                if (++c >= columns) break;
                renderer.result.append (',');
            }

            if (++r >= rows) break;
            renderer.result.append (";");
        }
        renderer.result.append ("]");
    }

    public Type eval (Instance context) throws EvaluationException
    {
        int columns = operands.length;
        if (columns == 0) return new Matrix ();
        int rows = operands[0].length;
        if (rows == 0) return new Matrix ();

        Matrix result = new Matrix (rows, columns);
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] == null)
                {
                    result.value[c][r] = 0;
                }
                else
                {
                    Type o = operands[c][r].eval (context);
                    if      (o instanceof Scalar) result.value[c][r] = ((Scalar) o).value;
                    else if (o instanceof Text  ) result.value[c][r] = Double.valueOf (((Text) o).value);
                    else if (o instanceof Matrix) result.value[c][r] = ((Matrix) o).value[0][0];
                    else throw new EvaluationException ("Can't construct matrix element from the given type.");
                }
            }
        }

        return result;
    }

    public String toString ()
    {
        Renderer renderer = new Renderer ();
        render (renderer);
        return renderer.result.toString ();
    }

    public int compareTo (Operator that)
    {
        Class<? extends Operator> thisClass = getClass ();
        Class<? extends Operator> thatClass = that.getClass ();
        if (! thisClass.equals (thatClass)) return thisClass.hashCode () - thatClass.hashCode ();

        // Same class as us, so compare operands
        BuildMatrix B = (BuildMatrix) that;
        int columns = operands.length;
        int difference = columns - B.operands.length;
        if (difference != 0) return difference;
        if (columns == 0) return 0;
        int rows = operands[0].length;
        difference = rows - B.operands[0].length;
        if (difference != 0) return difference;

        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                Operator a =   operands[c][r];
                Operator b = B.operands[c][r];
                if (a == b) continue;  // generally only true if both a and b are null
                if (a == null) return -1;
                if (b == null) return 1;
                difference = a.compareTo (b);
                if (difference != 0) return difference;
            }
        }

        return 0;
    }
}
