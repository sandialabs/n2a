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

import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OType;


public class NodeRef extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("oref.gif");

    // Core

    protected ORecordId ref;
    protected OType fieldType;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeRef(ORecordId r, OType ft) {
        ref = r;
        fieldType = ft;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public ORecordId getRef() {
        return ref;
    }
    public OType getFieldType() {
        return fieldType;
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
        return ref.clusterId + "#" + ref.clusterPosition;
    }
}
