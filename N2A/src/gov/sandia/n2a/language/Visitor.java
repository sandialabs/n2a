/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

/**
    A generic visitor for Operator.
**/
public class Visitor
{
    /**
        @return true to recurse below current node. false if further recursion below this node is not needed.
    **/
    public boolean visit (Operator op)
    {
        return true;
    }
}
