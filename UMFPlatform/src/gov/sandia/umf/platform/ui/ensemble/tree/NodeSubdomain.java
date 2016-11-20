/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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
