/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
