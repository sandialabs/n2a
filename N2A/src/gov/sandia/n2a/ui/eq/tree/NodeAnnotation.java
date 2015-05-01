/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeAnnotation extends NodeBase
{
    public static ImageIcon icon = ImageUtil.getImage ("about.gif");

    public String name;
    public String value;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeAnnotation (String namedValue)
    {
        setAnnotation (namedValue);
    }

    public NodeAnnotation (String name, String value)
    {
        this.name  = name;
        this.value = value;
    }

    public void setAnnotation (String namedValue)
    {
        String[] parts = namedValue.split ("=", 2);
        if (parts.length < 2)
        {
            name = namedValue;
            value = null;
        }
        else
        {
            name = parts[0];
            value = parts[1];
        }
    }

    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public String toString ()
    {
        if (value == null  ||  value.isEmpty ()) return name;
        return name + " = " + value;
    }
}
