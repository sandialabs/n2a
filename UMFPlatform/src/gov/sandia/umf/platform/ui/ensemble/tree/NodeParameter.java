/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.tree;

import gov.sandia.n2a.parms.Parameter;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeParameter extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final ImageIcon icon = ImageUtil.getImage("input.gif");

    // Core

    private Parameter param;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeParameter(Parameter p) {
        param = p;
    }


    //////////////
    // ACCESSOR //
    //////////////

    public Parameter getParameter() {
        return param;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public Icon getIcon(boolean expanded) {
        return (param.getIcon() == null ? icon : param.getIcon());
    }

    @Override
    public String toString() {
        return param.getKey().toString();
    }


    public void setShowDefaultValues(boolean show) {

    }
}
