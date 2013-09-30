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

import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;


public class NodeRecord extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("orecord.gif");

    // Core

    protected ODocument doc;
    protected String key;
    protected OType fieldType;

    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeRecord(ODocument d, String k, OType ft) {
        doc = d;
        key = k;
        fieldType = ft;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public ODocument getDocument() {
        return doc;
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
        if(key != null) {
            return key + " = " + doc.getIdentity().toString();
        }
        return doc.getIdentity().toString();
    }
}
