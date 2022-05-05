/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

public class ExportCshared extends ExportCstatic
{
    public ExportCshared ()
    {
        shared = true;
    }

    @Override
    public String getName ()
    {
        return "C shared library";
    }
}
