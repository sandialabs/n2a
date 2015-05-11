/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.language.parse.ASTNodeBase;
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

    public void getOperandsFrom (ASTNodeBase node)
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
}
