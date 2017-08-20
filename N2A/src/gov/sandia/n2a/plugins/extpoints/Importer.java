/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.io.File;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface Importer extends ExtensionPoint
{
    public String  getName ();
    public void    process (File source);
    public float   isIn    (File source);  // @return The probability that the file contains this format.
    public boolean accept  (File source);  // For the purpose of file dialog filtering. This should be a lightweight test, for example examining only the suffix.
}
