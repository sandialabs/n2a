/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import javax.measure.Unit;

import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Constant extends Operator
{
    public Type    value;
    public Unit<?> unit;  // If value is a scalar, then this is the original unit when it was parsed, or null if no unit was given. Note that value has already been scaled to SI, so this is merely a memo.

    public Constant ()
    {
    }

    public Constant (Type value)
    {
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public void getOperandsFrom (SimpleNode node)
    {
        UnitValue uv = (UnitValue) node.jjtGetValue ();
        unit = uv.unit;
        if (unit == null)  // naked number, so assume already in SI
        {
            value = new Scalar (uv.value);
        }
        else  // there was a unit given, so convert
        {
            @SuppressWarnings("rawtypes")
            Unit si = unit.getSystemUnit ();
            value = new Scalar (unit.getConverterTo (si).convert (uv.value));
        }
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

    public int compareTo (Operator that)
    {
        Class<? extends Operator> thisClass = getClass ();
        Class<? extends Operator> thatClass = that.getClass ();
        if (! thisClass.equals (thatClass)) return thisClass.hashCode () - thatClass.hashCode ();

        Constant c = (Constant) that;
        return value.compareTo (c.value);
    }
}
