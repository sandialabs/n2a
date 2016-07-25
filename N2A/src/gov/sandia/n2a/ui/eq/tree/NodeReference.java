/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeReference extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage("book.gif");

    public String index;
    public String comment;

    public NodeReference (String index, MNode node)
    {
        this.index = index;
        comment = node.get ();
    }

    public NodeReference (String index, String comment)
    {
        this.index = index;
        this.comment = comment;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public Color getForegroundColor ()
    {
        return Color.black;
    }

    @Override
    public String toString ()
    {
        return index + " -- " + comment;
    }
}
