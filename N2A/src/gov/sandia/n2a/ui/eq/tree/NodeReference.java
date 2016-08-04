/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;

public class NodeReference extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("book.gif");

    public String index;
    public String comment;

    public NodeReference (String index, MNode node)
    {
        this.index = index;
        comment = node.get ();
        setUserObject (index + " -- " + comment);
    }

    public NodeReference (String index, String comment)
    {
        this.index = index;
        this.comment = comment;
        setUserObject (index + " -- " + comment);
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
    }

    @Override
    public void add (String type, EquationTreePanel model)
    {
        NodeBase parent = (NodeBase) getParent ();
        if (type.isEmpty ()) parent.add ("Reference", model);  // By context, we assume the user wants to add another reference.
        else                 parent.add (type, model);
    }
}
