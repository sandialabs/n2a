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
    public Type      value;
    public UnitValue unitValue;  // Used for pretty printing. If value is a scalar, then this is the original object returned by the parser. Note that value has already been scaled to SI, so this is merely a memo.

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
