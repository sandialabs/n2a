/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.operator;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorLogical;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import tech.units.indriya.AbstractUnit;

public class NOT extends OperatorUnary implements OperatorLogical
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
        return 2;
    }

    public void determineExponent (Variable from)
    {
        operand.determineExponent (from);
        if (operand.exponent == UNKNOWN) return;
        if (operand.getType () instanceof Matrix)  // Matrix inverse
        {
            // matrix is A; inverse is !A
            // The idea is that the individual elements of !A should roughly multiply with corresponding
            // elements in A to produce scalars with magnitude around 1 (center power near 0).
            // Alternately, elementwise 1/A should have same power as !A.
            int cent = MSB / 2;
            int pow = 0 - operand.centerPower ();  // See Divide class. We're treating this as 1/A, where 1 has center power 0.
            pow += MSB - cent;
            updateExponent (from, pow, cent);
        }
        else  // Logical not
        {
            int centerNew   = MSB / 2;
            int exponentNew = MSB - centerNew;
            updateExponent (from, exponentNew, centerNew);
        }
    }

    public void determineExponentNext (Variable from)
    {
        operand.exponentNext = operand.exponent;
        operand.determineExponentNext (from);
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
