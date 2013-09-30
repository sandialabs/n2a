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

public class NodeJob extends NodeBase {
    protected static ImageIcon iconInProgress = ImageUtil.getImage("inprogress.gif");
    protected static ImageIcon iconComplete = ImageUtil.getImage("complete.gif");
    protected String jobName;
    protected boolean complete;

    public NodeJob(String n) {
        jobName = n;
    }
    public NodeJob(String n, boolean comp) {
        jobName = n;
        complete = comp;
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
        return complete ? iconComplete : iconInProgress;
    }

    public void setComplete(boolean comp) {
        complete = comp;
    }

    @Override
    public Color getForegroundColor() {
        return Color.black;
    }
}
