/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.tree;

import gov.sandia.n2a.parms.ParameterDomain;

import javax.swing.Icon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeSubdomain extends NodeBase {


    ///////////
    // FIELD //
    ///////////

    // Core

    private ParameterDomain subdomain;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeSubdomain(ParameterDomain s) {
        subdomain = s;
    }


    //////////////
    // ACCESSOR //
    //////////////

    public ParameterDomain getSubdomain() {
        return subdomain;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public Icon getIcon(boolean expanded) {
        return subdomain.getIcon();
    }
    @Override
    public String toString() {
        return subdomain.getName();
    }
}
