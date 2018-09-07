/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTConstant;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.MatrixDense;
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
                operands[0][r].parent = this;
            }
            else
            {
                int currentCols = row.jjtGetNumChildren ();
                for (int c = 0; c < currentCols; c++)
                {
                    operands[c][r] = Operator.getFrom ((SimpleNode) row.jjtGetChild (c));
                    operands[c][r].parent = this;
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
                    if (operands[c][r] == null) continue;
                    result.operands[c][r] = operands[c][r].deepCopy ();
                    result.operands[c][r].parent = result;
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

        Matrix A = new MatrixDense (rows, cols);  // potential constant to replace us
        boolean isConstant = true;  // any element that is not constant will change this to false
        int cent  = 0;  // for fixed-point analysis
        int pow   = 0;
        int count = 0;
        for (int c = 0; c < cols; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] == null)
                {
                    A.set (r, c, 0);
                }
                else
                {
                    operands[c][r] = operands[c][r].simplify (from);
                    if (isConstant)  // stop evaluating if we already know we are not constant
                    {
                        if (operands[c][r] instanceof Constant)
                        {
                            Constant op = (Constant) operands[c][r];
                            Type o = op.value;
                            if (o instanceof Scalar)
                            {
                                double v = ((Scalar) o).value;
                                A.set (r, c, v);

                                if (v != 0)
                                {
                                    int tempCent = MSB / 2;
                                    if (op.unitValue != null)
                                    {
                                        int bits = (int) Math.ceil (op.unitValue.digits * Math.log (10) / Math.log (2));
                                        tempCent = Math.max (tempCent, bits - 1);
                                        tempCent = Math.min (tempCent, MSB);
                                    }
                                    cent += tempCent;
                                    pow  += Math.getExponent (v);
                                    count++;
                                }
                            }
                            else if (o instanceof Text  ) A.set (r, c, Double.valueOf (((Text) o).value));
                            else if (o instanceof Matrix) A.set (r, c, ((Matrix) o).get (0, 0));
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

        if (isConstant)
        {
            from.changed = true;
            Constant result = new Constant (A);
            result.parent = parent;
            if (count > 0)
            {
                cent /= count;
                pow  /= count;
                result.center   = cent;
                result.exponent = pow + MSB - cent;
            }
            else
            {
                result.center   = MSB / 2;
                result.exponent = MSB - result.center;
            }
            return result;
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        int cent  = 0;
        int pow   = 0;
        int count = 0;
        for (Operator[] c : operands)
        {
            for (Operator e : c)
            {
                e.determineExponent (from);
                if (! (e instanceof Constant)  ||  e.getDouble () != 0)  // avoid counting zeros
                {
                    cent += e.center;
                    pow  += e.exponent;
                    count++;
                }
            }
        }
        if (count > 0)
        {
            cent /= count;
            pow  /= count;
        }
        else
        {
            cent = MSB / 2;
            pow  = MSB - cent;
        }
        updateExponent (from, pow, cent);
    }

    public void determineExponentNext (Variable from)
    {
        for (Operator[] c : operands)
        {
            for (Operator e : c)
            {
                e.exponentNext = exponentNext;
                e.determineExponentNext (from);
            }
        }
    }

    public void dumpExponents (String pad)
    {
        super.dumpExponents (pad);

        int columns = operands.length;
        if (columns == 0) return;
        int rows = operands[0].length;

        pad += "  ";
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] == null) continue;
                System.out.println (pad + r + "," + c + ": ");
                operands[c][r].dumpExponents (pad + "  ");
            }
        }
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

    public Type getType ()
    {
        return new MatrixDense ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        int columns = operands.length;
        if (columns == 0) return new MatrixDense ();
        int rows = operands[0].length;
        if (rows == 0) return new MatrixDense ();

        Matrix result = new MatrixDense (rows, columns);
        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                if (operands[c][r] == null)
                {
                    result.set (r, c, 0);
                }
                else
                {
                    Type o = operands[c][r].eval (context);
                    if      (o instanceof Scalar) result.set (r, c, ((Scalar) o).value);
                    else if (o instanceof Text  ) result.set (r, c, Double.valueOf (((Text) o).value));
                    else if (o instanceof Matrix) result.set (r, c, ((Matrix) o).get (0, 0));
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

    public boolean equals (Object that)
    {
        if (! (that instanceof BuildMatrix)) return false;
        BuildMatrix B = (BuildMatrix) that;

        int columns = operands.length;
        if (columns != B.operands.length) return false;
        if (columns == 0) return true;
        int rows = operands[0].length;
        if (rows != B.operands[0].length) return false;

        for (int c = 0; c < columns; c++)
        {
            for (int r = 0; r < rows; r++)
            {
                Operator a =   operands[c][r];
                Operator b = B.operands[c][r];
                if (a == b) continue;  // generally only true if both a and b are null
                if (a == null) return false;
                if (b == null) return false;
                if (! a.equals (b)) return false;
            }
        }

        return true;
    }
}
