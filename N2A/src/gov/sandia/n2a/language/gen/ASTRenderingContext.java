/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.gen;

import java.util.HashMap;


public class ASTRenderingContext
{
    public boolean shortMode;
    public HashMap<Class<? extends ASTNodeBase>, ASTNodeRenderer> renderers;

    public ASTRenderingContext (boolean shortMode)
    {
        this.shortMode = shortMode;
    }

    public void add (Class<? extends ASTNodeBase> c, ASTNodeRenderer x)
    {
        if (renderers == null)
        {
            renderers = new HashMap<Class<? extends ASTNodeBase>, ASTNodeRenderer> ();
        }
        renderers.put (c, x);
    }

    /**
        Apply any specialized renderers while processing the given node and all its children.
    **/
    public String render (ASTNodeBase node)
    {
        if (renderers != null)
        {
            ASTNodeRenderer r = renderers.get (node.getClass ());
            if (r != null) return r.render (node, this);
        }
        return node.render (this);
    }
}
