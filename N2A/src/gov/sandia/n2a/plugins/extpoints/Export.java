/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.ExtensionPoint;

import java.nio.file.Path;


public interface Export extends ExtensionPoint
{
    public String  getName ();
    /**
        Writes the given document to a file at the given path.
        Called on separate thread from EDT.
        When called, Backend.err is set up with a suitable PrintStream. If the export is fully
        successful, write nothing to err. If the export is partially successful, write suitable
        warnings. If the export fails, throw an exception.
    **/
    public void    process (MNode document, Path destination) throws Exception;
    public boolean accept  (Path source);  // For the purpose of file dialog filtering. This should be a lightweight test, for example examining only the suffix.
}
