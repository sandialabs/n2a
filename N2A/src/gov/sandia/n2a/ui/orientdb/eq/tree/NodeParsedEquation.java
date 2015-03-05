/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.eq.tree;

import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeParsedEquation extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("expr.gif");

    // Core

    protected ParsedEquation peq;  // Sync'ed with current DB record.


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeParsedEquation(ParsedEquation p) {
        peq = p;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    //??


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
    public Color getForegroundColor() {
        return Color.black;
    }
    @Override
    public String toString() {
        String eqText;
        if(AppState.getState().isEqnFormat()) {
            eqText = peq.getTree().toReadableShort();
        } else {
            eqText = peq.getTree().getSource();    // Done this way to remove annotations.
        }
        return eqText;
    }
}
