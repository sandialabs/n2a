/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.nio.file.Path;

@SuppressWarnings("serial")
public class NodeOutput extends NodeFile
{
    public NodeOutput (Path path)
    {
        super (path);
        priority = 1;
        icon     = iconOut;
        setUserObject ("Output");
    }
}
