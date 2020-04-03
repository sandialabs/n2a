/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import tech.units.indriya.AbstractUnit;

public class OR extends OperatorBinary implements OperatorLogical
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "||";
            }

            public Operator createInstance ()
            {
                return new OR ();
            }
        };
    }

    public int precedence ()
    {
        return 9;
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
                if (value == 0) result = operand1;
                else            result = new Constant (1);
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
                if (value == 0) result = operand0;
                else            result = new Constant (1);
                result.parent = parent;
                return result;
            }
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        operand0.determineExponent (from);
        operand1.determineExponent (from);
        int centerNew   = MSB / 2;
        int exponentNew = MSB - centerNew;
        updateExponent (from, exponentNew, centerNew);
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operand0.determineUnit (fatal);
        operand1.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        return operand0.eval (context).OR (operand1.eval (context));
    }

    public String toString ()
    {
        return "||";
    }
}
