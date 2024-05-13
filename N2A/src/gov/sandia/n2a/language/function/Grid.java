/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;
import tech.units.indriya.AbstractUnit;

public class Grid extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "grid";
            }

            public Operator createInstance ()
            {
                return new Grid ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++)  // This works, even if last operand is a string.
        {
            operands[i].determineExponent (context);
        }

        boolean raw = getKeywordFlag ("raw");
        if (raw) updateExponent (context, 0,        0);       // integer
        else     updateExponent (context, -1 - MSB, MSB - 1); // Since output never quite reaches 1, all bits can be fractional.
    }

    public void determineExponentNext ()
    {
        for (int i = 0; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = 0;  // grid() requires integers
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        return new MatrixDense ();
    }

    public Type eval (Instance context) throws EvaluationException
    {
        // collect parameters into arrays
        int i = (int) Math.floor (((Scalar) operands[0].eval (context)).value);
        int nx = 1;
        int ny = 1;
        int nz = 1;
        if (operands.length >= 2) nx = (int) Math.floor (((Scalar) operands[1].eval (context)).value);
        if (operands.length >= 3) ny = (int) Math.floor (((Scalar) operands[2].eval (context)).value);
        if (operands.length >= 4) nz = (int) Math.floor (((Scalar) operands[3].eval (context)).value);

        // compute xyz in stride order
        Matrix result = new MatrixDense (3, 1);
        int sx = ny * nz;  // stride x
        if (getKeywordFlag ("raw"))
        {
            result.set (0, i / sx);  // (i / sx) is an integer operation, so remainder is truncated.
            i %= sx;
            result.set (1, i / nz);
            result.set (2, i % nz);
        }
        else
        {
            result.set (0, ((i / sx) + 0.5) / nx);
            i %= sx;
            result.set (1, ((i / nz) + 0.5) / ny);
            result.set (2, ((i % nz) + 0.5) / nz);
        }
        return result;
    }

    public String toString ()
    {
        return "grid";
    }
}
