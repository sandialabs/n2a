/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.model.topotree;

import gov.sandia.n2a.data.Bridge;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeBridge extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("bridge.gif");

    // Core

    protected Bridge bridge;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeBridge(Bridge br) {
        bridge = br;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public Bridge getBridge() {
        return bridge;
    }
    public void setBridge(Bridge br) {
        bridge = br;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public boolean isCollapsible() {
        return false;
    }
    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }
    @Override
    public String toString() {
        return bridge.getName() + " (connection: " + bridge.getConnectionName() + ")";
    }
}
