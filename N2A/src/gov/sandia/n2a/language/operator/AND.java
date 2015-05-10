/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;

public class AND extends OperatorBinary
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "&&";
            }

            public Operator createInstance ()
            {
                return new AND ();
            }
        };
    }

    public int precedence ()
    {
        return 8;
    }

    public Operator simplify (Variable from)
    {
        Operator result = super.simplify (from);
        if (result != this) return result;

        if (operand0 instanceof Constant)
        {
            Type c0 = ((Constant) operand0).value;
            if (c0 instanceof Scalar)
            {
                double value = ((Scalar) c0).value;
                if (value == 0) return new Constant (new Scalar (0));
                else            return operand1;
            }
        }
        else if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar)
            {
                double value = ((Scalar) c1).value;
                if (value == 0) return new Constant (new Scalar (0));
                else            return operand0;
            }
        }
        return this;
    }

    public Type eval (EvaluationContext context)
    {
        return operand0.eval (context).AND (operand1.eval (context));
    }

    public String toString ()
    {
        return "&&";
    }
}
