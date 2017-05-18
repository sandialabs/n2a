/*
Copyright 2013,2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.jobs;

import java.io.File;

import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public class NodeFile extends NodeBase
{
    public enum Type
    {
        Model   ("Model",   "file_in.gif"),
        Output  ("Output",  "file_out.gif"),
        Error   ("Error",   "file_err.gif"),
        Result  ("Result",  "file_prn.gif"),
        Console ("Console", "file_cout.gif"),
        Other   ("Other",   "file_obj.gif");

        public String    label;
        public ImageIcon icon;

        Type (String lbl, String fileName)
        {
            label = lbl;
            icon = ImageUtil.getImage (fileName);
        }
    }

    protected File path;
    protected Type type;

    public NodeFile (Type type, File path)
    {
        this.path = path;
        this.type = type;
        if (type != Type.Other) setUserObject (type.label);
        else                    setUserObject (path.getName ());
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return type.icon;
    }
}
