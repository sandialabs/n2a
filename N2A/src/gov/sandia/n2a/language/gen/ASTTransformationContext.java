/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.gen;

import java.util.HashMap;


public class ASTTransformationContext
{
    public HashMap<Class<? extends ASTNodeBase>, ASTNodeTransformer> transformers;

    public void add (Class<? extends ASTNodeBase> c, ASTNodeTransformer x)
    {
        if (transformers == null)
        {
            transformers = new HashMap<Class<? extends ASTNodeBase>, ASTNodeTransformer> ();
        }
        transformers.put (c, x);
    }

    public ASTNodeBase transform (ASTNodeBase node)
    {
        if (transformers == null) return node;
        ASTNodeTransformer x = transformers.get (node.getClass ());
        if (x == null) return node;
        return x.transform (node);
    }
}
