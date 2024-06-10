/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface ShutdownHook extends ExtensionPoint
{
    /**
        An opportunity to do final work before the app shuts down.
        This is called before AppData is flushed to disk, so it is OK to store final
        state by updating objects contained in AppData.
    **/
    public void shutdown ();
}
