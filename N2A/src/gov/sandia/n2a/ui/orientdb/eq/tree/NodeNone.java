/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.eq.tree;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeNone extends NodeBase {
    protected static ImageIcon icon = ImageUtil.getImage("none.gif");
    protected String msg;

    public NodeNone(String a) {
        msg = a;
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }

    @Override
    public boolean isItalic() {
        return true;
    }

    @Override
    public String toString() {
        return msg;
    }
}
