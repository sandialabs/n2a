/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.gen;

public interface ASTNodeTransformer
{
    /**
        Modify the contents of a node or completely replace it in the tree.
        @return Either the given node object or a new object constructed by this method.
    **/
    public ASTNodeBase transform (ASTNodeBase node);
}