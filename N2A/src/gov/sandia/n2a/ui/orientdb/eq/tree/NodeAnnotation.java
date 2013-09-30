/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.eq.tree;

import gov.sandia.n2a.parsing.Annotation;
import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeAnnotation extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("about.gif");

    // Core

    protected Annotation annot;  // Parsing-Related Object


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeAnnotation(Annotation a) {
        annot = a;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public Annotation getAnnotation() {
        return annot;
    }
    public void setAnnotation(Annotation eq) {
        annot = eq;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }

    @Override
    public String toString() {
        if(AppState.getState().isEqnFormat()) {
            return annot.getTree().toReadableShort();
        }
        return annot.getTree().getSource();
    }
}
