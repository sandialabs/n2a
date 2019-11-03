/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import java.util.ArrayList;

public class VariableReference
{
    public Variable          variable;
    public ArrayList<Object> resolution = new ArrayList<Object> ();  // Trail of objects followed to resolve the variable. The first one is always variable.container, so it is not included in the list.
    public int               index      = -1;   // Internal backend data, for looking up resolved Instance. -1 means unresolved

    public boolean equals (Object o)
    {
        if (! (o instanceof VariableReference)) return false;
        VariableReference that = (VariableReference) o;
        if (variable != that.variable) return false;
        if (! resolution.equals (that.resolution)) return false;
        return true;
    }
}
