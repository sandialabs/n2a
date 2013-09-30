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

import replete.gui.controls.simpletree.NodeBase;


public class NodeCluster extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("ocluster.gif");

    // Core

    protected String name;
    protected int id;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeCluster(String nm, int i) {
        name = nm;
        id = i;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public String getName() {
        return name;
    }
    public int getId() {
        return id;
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
        return name + "#" + id;
    }
}
