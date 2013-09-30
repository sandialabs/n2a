/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.model.topotree;

import gov.sandia.n2a.data.Layer;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeBridgeLayer extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("layer.gif");

    // Core

    protected Layer layer;
    protected String alias;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeBridgeLayer(Layer ly, String al) {
        layer = ly;
        alias = al;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public boolean isCollapsible() {
        return true;
    }
    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }
    @Override
    public String toString() {
        return layer.getName() + " (equation alias: " + alias + ")";
    }
}
