/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
        Model   (0, "Model",       "file_in.gif"),
        Output  (1, "Output",      "file_out.gif"),
        Error   (0, "Diagnostics", "file_err.gif"),
        Result  (2, "Result",      "file_prn.gif"),
        Console (0, "Console",     "file_cout.gif"),
        Other   (0, "Other",       "file_obj.gif"),
        Video   (2, "Other",       "file_obj.gif"),
        Picture (2, "Other",       "file_obj.gif");

        public int       priority;  // For choosing primary output file. 0 means don't ever select as output.
        public String    label;
        public ImageIcon icon;

        Type (int p, String lbl, String fileName)
        {
            priority = p;
            label    = lbl;
            icon     = ImageUtil.getImage (fileName);
        }
    }

    protected Path    path;
    protected Type    type;
    protected boolean found;  // Flag to indicate if associated file was found during last scan.

    public NodeFile (Type type, Path path)
    {
        this.path = path;
        this.type = type;
        if (type.label.equals ("Other")) setUserObject (path.getFileName ().toString ());
        else                             setUserObject (type.label);
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return type.icon;
    }
}
