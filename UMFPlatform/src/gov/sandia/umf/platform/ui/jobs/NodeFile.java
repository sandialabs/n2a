/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui.jobs;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

// Root node in tree is just a string.
public class NodeFile extends NodeBase {
    public enum Type {
        Model   ("Model",   "file_in.gif"),
        Output  ("Output",  "file_out.gif"),
        Error   ("Error",   "file_err.gif"),
        Result  ("Result",  "file_prn.gif"),
        Console ("Console", "file_cout.gif"),
        Other   ("Other",   "file_obj.gif");

        private String label;
        private ImageIcon icon;
        Type(String lbl, String fileName) {
            label = lbl;
            icon = ImageUtil.getImage(fileName);
        }
        public String getLabel() {
            return label;
        }
        public ImageIcon getIcon() {
            return icon;
        }
    }

    protected String remotePath;
    protected Type type;

    public NodeFile(Type t, String p) {
        remotePath = p;
        type = t;
    }

    @Override
    public String toString() {
        return type.getLabel();
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return type.getIcon();
    }

    public Type getType() {
        return type;
    }

    public String getRemotePath() {
        return remotePath;
    }

    @Override
    public Color getForegroundColor() {
        return Color.black;
    }
}
