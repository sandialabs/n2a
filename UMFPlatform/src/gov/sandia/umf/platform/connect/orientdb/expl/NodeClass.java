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

import com.orientechnologies.orient.core.metadata.schema.OClass;


public class NodeClass extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("oclass.gif");
    protected static ImageIcon icon2 = ImageUtil.getImage("oclassul.gif");

    // Core

    protected boolean loaded;
    protected String name;
    protected int id;
    protected OClass clazz;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeClass(String nm, int i, OClass c) {
        name = nm;
        id = i;
        clazz = c;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public String getName() {
        return name;
    }
    public int getId() {
        return id;
    }
    public OClass getOClass() {
        return clazz;
    }

    public boolean isLoaded() {
        return loaded;
    }
    public void setLoaded(boolean load) {
        loaded = load;
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
        return loaded ? icon : icon2;
    }
    @Override
    public String toString() {
        return name + "#" + id;
    }
}
