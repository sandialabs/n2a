/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.MatrixVisitable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class SquareRoot extends Function implements MatrixVisitable
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "sqrt";
            }

            public Operator createInstance ()
            {
                return new SquareRoot ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        Operator op = operands[0];
        op.determineExponent (context);

        if (op.exponent == UNKNOWN) return;
        int pow = op.centerPower () / 2;
        int centerNew   = MSB / 2;
        int exponentNew = pow - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        Operator op = operands[0];
        op.exponentNext = op.exponent;
        op.determineExponentNext ();
    }

    public boolean hasExponentA ()
    {
        return true;
    }

    public boolean hasExponentResult ()
    {
        return true;
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        Operator op = operands[0];
        op.determineUnit (fatal);
        unit = null;
        if (op.unit != null)
        {
            try
            {
                unit = UnitValue.simplify (op.unit.root (2));  // Should also work for ONE.
            }
            catch (ArithmeticException error)
            {
                // TODO: should this be fatal?
            }
        }
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.sqrt (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit
            (
                new Matrix.Visitor ()
                {
                    public double apply (double a)
                    {
                        return Math.sqrt (a);
                    }
                }
            );
        }
        throw new EvaluationException ("type mismatch");
    }

    public void solve (Equality statement) throws EvaluationException
    {
        statement.lhs = operands[0];
        Power square = new Power ();
        square.operand0 = statement.rhs;
        square.operand1 = new Constant (2);
        statement.rhs = square;
    }

    public String toString ()
    {
        return "sqrt";
    }
}
