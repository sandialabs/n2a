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

import replete.gui.controls.simpletree.NodeSimpleLabel;

import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public class NodeBytes extends NodeSimpleLabel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("obytes.gif");

    // Core

    private byte[] bytes;
    private ORecordBytes rec;
    protected OType fieldType;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeBytes(String l, byte[] b, ORecordBytes r, OType ft) {
        super(l);
        bytes = b;
        rec = r;
        fieldType = ft;
    }


    //////////////
    // ACCESSOR //
    //////////////

    public byte[] getBytes() {
        return bytes;
    }
    public ORecordBytes getRecord() {
        return rec;
    }
    public OType getFieldType() {
        return fieldType;
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
        return rec.getIdentity().toString() + " " + super.toString();
    }
}
