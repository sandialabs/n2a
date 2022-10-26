/*
Copyright 2020-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.MatrixVisitable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Matrix;

public class Ceil extends Function implements MatrixVisitable
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "ceil";
            }

            public Operator createInstance ()
            {
                return new Ceil ();
            }
        };
    }

    public void determineExponent (ExponentContext context)
    {
        Round.determineExponentStatic (this, context);
    }

    public void determineExponentNext ()
    {
        Round.determineExponentNextStatic (this, exponentNext);
    }

    public boolean hasExponentA ()
    {
        return true;
    }

    public boolean hasExponentResult ()
    {
        return true;
    }

    public Type eval (Instance context)
    {
        Type arg = operands[0].eval (context);
        if (arg instanceof Scalar) return new Scalar (Math.ceil (((Scalar) arg).value));
        if (arg instanceof Matrix)
        {
            return ((Matrix) arg).visit (new Matrix.Visitor ()
            {
                public double apply (double a)
                {
                    return Math.ceil (a);
                }
            });
        }
        throw new EvaluationException ("type mismatch");
    }

    public String toString ()
    {
        return "ceil";
    }
}
