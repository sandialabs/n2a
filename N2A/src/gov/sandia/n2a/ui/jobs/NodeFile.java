/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.jobs;

import java.nio.file.Path;

import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

@SuppressWarnings("serial")
public class NodeFile extends NodeBase
{
    public enum Type
    {
        Model   ("Model",       "file_in.gif"),
        Output  ("Output",      "file_out.gif"),
        Error   ("Diagnostics", "file_err.gif"),
        Result  ("Result",      "file_prn.gif"),
        Console ("Console",     "file_cout.gif"),
        Other   ("Other",       "file_obj.gif");

        public String    label;
        public ImageIcon icon;

        Type (String lbl, String fileName)
        {
            label = lbl;
            icon = ImageUtil.getImage (fileName);
        }
    }

    protected Path    path;
    protected Type    type;
    protected boolean found;  // Flag to indicate if associated file was found during last scan.

    public NodeFile (Type type, Path path)
    {
        this.path = path;
        this.type = type;
        if (type == Type.Other) setUserObject (path.getFileName ().toString ());
        else                    setUserObject (type.label);
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return type.icon;
    }
}
