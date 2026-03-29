/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.nio.file.Path;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface Import extends ExtensionPoint
{
    public String  getName ();
    /**
        Reads in the source and adds the contents to the DB.
        Called on separate thread from EDT. Any GUI updates must be explicitly placed onto the EDT.
        When called, Backend.err is set up with a suitable PrintStream. If the import is fully
        successful, write nothing to err. If the import is partially successful, write suitable
        warnings. If the import fails, throw an exception.
        @param name A hint for the internal key of the created record. May be null. May be ignored by some importers.
    **/
    public void    process (Path source, String name) throws Exception;
    public float   matches (Path source);  // @return The probability that the file contains this format.
    public boolean accept  (Path source);  // For the purpose of file dialog filtering. This should be a lightweight test, for example examining only the suffix.
}
