/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Text;

public class Constant extends Operator
{
    public Type value;

    public Constant ()
    {
    }

    public Constant (Type value)
    {
        this.value = value;
    }

    public void getOperandsFrom (SimpleNode node)
    {
        value = (Type) node.jjtGetValue ();
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
