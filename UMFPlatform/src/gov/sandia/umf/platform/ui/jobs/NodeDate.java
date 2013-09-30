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

public class NodeDate extends NodeBase {
    protected static ImageIcon iconOpen = ImageUtil.getImage("dateopen.gif");
    protected static ImageIcon iconClosed = ImageUtil.getImage("dateclosed.gif");
    protected String jobName;

    public NodeDate(String n) {
        jobName = n;
    }

    @Override
    public String toString() {
        return jobName;
    }

    @Override
    public boolean equals(Object o) {
        if(o == null || !(o instanceof NodeDate)) {
            return false;
        }
        return jobName.equals(((NodeDate) o).jobName);
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return expanded ? iconOpen : iconClosed;
    }

    @Override
    public Color getForegroundColor() {
        return Color.black;
    }
}
