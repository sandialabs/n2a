/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.eq.tree;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodePart extends NodeBase {
    protected static ImageIcon icon = ImageUtil.getImage("comp.gif");
    protected static ImageIcon icon2 = ImageUtil.getImage("conn.gif");

    private NDoc part;
    private String pre;
    private String alias;

    public NodePart(NDoc par, String p, String al) {
        part = par;
        pre = p;
        alias = al;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return ((String) part.get("type")).equalsIgnoreCase("compartment") ? icon : icon2;
    }

    @Override
    public Color getForegroundColor() {
        return Color.black;
    }

    public NDoc getPart() {
        return part;
    }

    @Override
    public String toString() {
        return (pre == null ? "" : pre + ": ") + part.get("name") + (alias == null ? "" : " (alias: " + alias + ")");
    }
}
