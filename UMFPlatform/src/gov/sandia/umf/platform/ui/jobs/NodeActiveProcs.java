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
public class NodeActiveProcs extends NodeBase {
    protected static ImageIcon icon = ImageUtil.getImage("active.gif");
    private int howMany;

    public NodeActiveProcs(int hm) {
        howMany = hm;
    }

    @Override
    public String toString() {
        return "Active Jobs (" + howMany + ")";
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }

    @Override
    public Color getForegroundColor() {
        return Color.black;
    }
}
