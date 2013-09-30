/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import java.util.LinkedList;

public class VariableReference
{
    public Variable           variable;
    public LinkedList<Object> resolution = new LinkedList<Object> (); // Trail of objects followed to resolve the variable. A list of all EquationSets visited. The first one is always variable.container, so it is not included in the list. If variable.container is the end of the search, then this list is never initialized.
}
