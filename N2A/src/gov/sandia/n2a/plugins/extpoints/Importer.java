/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.io.File;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface Importer extends ExtensionPoint
{
    public String getName ();
    public void   process (File source);
}
