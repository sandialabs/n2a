/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;

public class NodeBinding extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("connect.gif");

    public String name;
    public String alias;

    public NodeBinding (String alias, String name)
    {
        this.name  = name;
        this.alias = alias;
        setUserObject (alias + " = " + name);
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
    }

    public void parseEditedString (String input)
    {
    }
}
