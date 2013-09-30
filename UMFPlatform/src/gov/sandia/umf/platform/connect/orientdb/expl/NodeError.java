/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.expl.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeSimpleLabel;

// Root node in tree is just a string.
public class NodeError extends NodeSimpleLabel {

    protected static ImageIcon icon = ImageUtil.getImage("error.gif");

    public NodeError(String l) {
        super(l);
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }
}
