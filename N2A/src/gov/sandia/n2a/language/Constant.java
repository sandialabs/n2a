/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import javax.measure.Unit;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Constant extends Operator
{
    public Type      value;
    public UnitValue unitValue;  // Used for pretty printing. If value is a scalar, then this is the original object returned by the parser. Note that value has already been scaled to SI, so this is merely a memo.

    public Constant ()
    {
    }

    public Constant (Type value)
    {
        this.value = value;
    }

    public Constant (double value)
    {
        this.value = new Scalar (value);
    }

    @SuppressWarnings("unchecked")
    public void getOperandsFrom (SimpleNode node)
    {
        Object o = node.jjtGetValue ();
        if (o instanceof UnitValue)
        {
            unitValue = (UnitValue) node.jjtGetValue ();
            Unit<?> unit = unitValue.unit;
            if (unit == null)  // naked number, so assume already in SI
            {
                value = new Scalar (unitValue.value);
            }
            else  // there was a unit given, so convert
            {
                @SuppressWarnings("rawtypes")
                Unit si = unit.getSystemUnit ();
                value = new Scalar (unit.getConverterTo (si).convert (unitValue.value));
            }
        }
        else
        {
            value = (Type) o;
        }
    }

    public void determineExponent (Variable from)
    {
        if (exponent != Integer.MIN_VALUE) return;  // already done
        if (value instanceof Scalar)
        {
            double v = ((Scalar) value).value;
            int exponentNew;
            int centerNew = MSB / 2 + 1;
            if (v == 0)
            {
                exponentNew = MSB - centerNew;
            }
            else
            {
                int e = Math.getExponent (v);
                if (unitValue != null)
                {
                    int bits = (int) Math.ceil (unitValue.digits * Math.log (10) / Math.log (2));
                    centerNew = Math.max (centerNew, bits);
                    centerNew = Math.min (centerNew, MSB);
                }
                exponentNew = e + MSB - centerNew;
            }
            updateExponent (from, exponentNew, centerNew);
        }
        // Matrix constants are built by BuildMatrix with their exponent and center values set correctly.
        // Text and reference types are simply ignored (and should have exponent=Integer.MIN_VALUE).
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        if (value instanceof Text) renderer.result.append ("\"" + value.toString () + "\"");
        else                       renderer.result.append (value.toString ());
    }

    public Type eval (Instance context)
    {
        return value;
    }

    public String toString ()
    {
        return value.toString ();
    }

    public boolean equals (Object that)
    {
        if (! (that instanceof Constant)) return false;
        Constant c = (Constant) that;
        return value.equals (c.value);
    }
}
