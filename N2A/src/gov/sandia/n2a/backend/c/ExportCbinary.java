/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

public class ExportCbinary extends ExportCstatic
{
    public ExportCbinary ()
    {
        shared = true;  // Default choice for binaries, unless overridden by metadata in model itself.
        lib    = false;
    }

    @Override
    public String getName ()
    {
        return "C binary";
    }
}
