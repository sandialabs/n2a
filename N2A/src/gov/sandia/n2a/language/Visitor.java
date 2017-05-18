/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
