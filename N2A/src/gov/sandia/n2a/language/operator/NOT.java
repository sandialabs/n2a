/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorLogicalInput;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import tech.units.indriya.AbstractUnit;

public class NOT extends OperatorUnary implements OperatorLogicalInput
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "!";
            }

            public Operator createInstance ()
            {
                return new NOT ();
            }
        };
    }

    public Associativity associativity ()
    {
        return Associativity.RIGHT_TO_LEFT;
    }

    public int precedence ()
    {
        return 3;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        Operator result = super.simplify (from, evalOnly);
        if (result instanceof Constant)
        {
            Constant c = (Constant) result;
            if (c.value instanceof Matrix)
            {
                // Divide 1/operand
                // center power = 0 - (operand center power)
                // shift so center is at MSB/2
                if (operand.exponent == UNKNOWN) return result;
                result.center   = MSB / 2;
                result.exponent = 0 - operand.centerPower () - result.center;
            }
        }
        return result;
    }

    public void determineExponent (ExponentContext context)
    {
        operand.determineExponent (context);
        if (operand.getType () instanceof Matrix)  // Matrix inverse
        {
            // matrix is A; inverse is !A
            // The idea is that the individual elements of !A should roughly multiply with corresponding
            // elements in A to produce scalars with magnitude around 1 (center power near 0).
            // Alternately, elementwise 1/A should have same power as !A.
            if (operand.exponent == UNKNOWN) return;
            int cent = MSB / 2;
            int pow = 0 - operand.centerPower ();  // See Divide class. We're treating this as 1/A, where 1 has center power 0.
            pow -= cent;
            updateExponent (context, pow, cent);
        }
        else  // Logical not
        {
            updateExponent (context, 0, 0);
        }
    }

    public void determineExponentNext ()
    {
        operand.exponentNext = operand.exponent;
        operand.determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operand.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type eval (Instance context)
    {
        return operand.eval (context).NOT ();
    }

    public String toString ()
    {
        return "!";
    }
}
