/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.umf.platform.runs.Run;

public interface Analyzer extends ExtensionPoint
{
    public void analyze (Run[] runs);
}
