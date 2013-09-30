/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.eq.tree;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeSummaryRoot extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    public static final String CALC = "Calculating...";
    public static final String ALLEQ = "All Equations";
    public static final String ERROR = "Error: Cannot compute summary.";
    protected static ImageIcon icon = ImageUtil.getImage("comp.gif");
    protected static ImageIcon icon2 = ImageUtil.getImage("conn.gif");

    // State

    private String type;
    private String str = CALC;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeSummaryRoot(String t) {
        type = t;
    }


    /////////////
    // MUTATOR //
    /////////////

    public void setString(String s) {
        str = s;
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
        return type.equalsIgnoreCase("compartment") ? icon : icon2;
    }

    @Override
    public Color getForegroundColor() {
        return Color.blue;
    }

    @Override
    public String toString() {
        return str;
    }
}
