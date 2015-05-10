/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

/**
    A visitor for Operator which replaces the current node with a rewritten tree.
**/
public class Transformer
{
    /**
        @return The modified Operator, or null if no action was taken. When null is
        returned, the Operator performs its own default action, which is generally
        to recurse down the tree.
    **/
    public Operator transform (Operator op)
    {
        return null;
    }
}
