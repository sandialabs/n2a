/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import java.util.LinkedList;

public class VariableReference
{
    public Variable           variable;
    public LinkedList<Object> resolution = new LinkedList<Object> (); // Trail of objects followed to resolve the variable. A list of all EquationSets visited. The first one is always variable.container, so it is not included in the list. If variable.container is the end of the search, then this list is never initialized.
    public int                index      = -1;   // Internal backend data, for looking up resolved Instance. -1 means unresolved
}
