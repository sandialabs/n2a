/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.OperatorLogical;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class AND extends OperatorBinary implements OperatorLogical
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
                from.changed = true;
                double value = ((Scalar) c0).value;
                result = operand1;
                if (value == 0)
                {
                    operand1.releaseDependencies (from);
                    result = new Constant (0);
                }
                result.parent = parent;
                return result;
            }
        }
        else if (operand1 instanceof Constant)
        {
            Type c1 = ((Constant) operand1).value;
            if (c1 instanceof Scalar)
            {
                from.changed = true;
                double value = ((Scalar) c1).value;
                result = operand0;
                if (value == 0)
                {
                    operand0.releaseDependencies (from);
                    result = new Constant (0);
                }
                result.parent = parent;
                return result;
            }
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand0.exponentNext = operand0.exponent;
        operand1.exponentNext = operand1.exponent;
        operand0.determineExponent (from);
        operand1.determineExponent (from);
        int centerNew   = MSB / 2;
        int exponentNew = MSB - centerNew;
        updateExponent (from, exponentNew, centerNew);
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).AND (operand1.eval (context));
    }

    public String toString ()
    {
        return "&&";
    }
}
