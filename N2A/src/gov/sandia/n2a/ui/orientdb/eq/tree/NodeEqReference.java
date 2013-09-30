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

public class NodeEqReference extends NodeBase {

    ////////////
    // FIELDS //
    ////////////

    // Static

    protected static ImageIcon icon = ImageUtil.getImage("book.gif");

    // Core

    protected NDoc eqRef;

    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeEqReference(NDoc eqr) {
        eqRef = eqr;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessor

    public NDoc getEqReference() {
        return eqRef;
    }

    // Mutator

    public void setEqReference(NDoc eqr) {
        eqRef = eqr;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

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
        int maxLen = 25;
        NDoc ref = (NDoc) eqRef.get("ref");
        String t = ref.get("title", "");
        String a = ref.get("author", "");
        if(t.length() > maxLen) {
            t = t.substring(0, maxLen) + "...";
        }
        if(a.length() > maxLen) {
            a = a.substring(0, maxLen) + "...";
        }
        return t + " [by] " + a;
    }
}
