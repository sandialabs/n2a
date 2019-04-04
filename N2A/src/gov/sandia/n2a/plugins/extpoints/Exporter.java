/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.ExtensionPoint;

import java.nio.file.Path;


public interface Exporter extends ExtensionPoint
{
    public String getName ();
    public void   export (MNode document, Path destination);
}
