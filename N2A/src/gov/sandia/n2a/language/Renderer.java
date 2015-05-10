/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

/**
    A visitor for Operator which collects a string suitable for either display
    or code generation. Subclass this to override the default rendering built
    into Operators.
**/
public class Renderer
{
    public StringBuilder result;

    public Renderer ()
    {
        result = new StringBuilder ();
    }

    public Renderer (StringBuilder result)
    {
        this.result = result;
    }

    /**
        @return true if this function rendered the operator. false if the operator should render itself (using its default method).
    **/
    public boolean render (Operator op)
    {
        return false;
    }
}
