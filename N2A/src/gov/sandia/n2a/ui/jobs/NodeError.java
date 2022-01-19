/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.nio.file.Path;

@SuppressWarnings("serial")
public class NodeError extends NodeFile
{
    public NodeError (Path path)
    {
        super (path);
        icon = iconErr;
        setUserObject ("Diagnostics");
    }

    @Override
    public boolean couldHaveColumns ()
    {
        return false;
    }

    @Override
    public boolean isGraphable ()
    {
        return false;
    }
}
