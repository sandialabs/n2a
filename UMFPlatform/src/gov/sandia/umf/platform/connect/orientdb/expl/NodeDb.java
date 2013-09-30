/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.expl.images.ImageUtil;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeDb extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("db.gif");

    // Core

    protected OrientConnectDetails cxnDetails;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeDb(OrientConnectDetails c) {
        cxnDetails = c;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public OrientConnectDetails getConnection() {
        return cxnDetails;
    }
    public void setConnection(OrientConnectDetails c) {
        cxnDetails = c;
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
        return cxnDetails.getName() + " [" + cxnDetails.getLocation() + "]";
    }
}
