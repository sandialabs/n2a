/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.io.File;
import java.nio.file.Path;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface Importer extends ExtensionPoint
{
    public String  getName ();
    public void    process (Path source);
    public float   isIn    (Path source);  // @return The probability that the file contains this format.
    public boolean accept  (File source);  // For the purpose of file dialog filtering. This should be a lightweight test, for example examining only the suffix.
}
