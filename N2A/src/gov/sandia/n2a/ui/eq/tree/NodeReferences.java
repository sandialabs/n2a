/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

public class NodeReferences extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeReferences (MPart source)
    {
        this.source = source;
        setUserObject ("$reference");
    }

    public void build (DefaultTreeModel model)
    {
        for (MNode c : source)
        {
            model.insertNodeInto (new NodeReference ((MPart) c), this, this.getChildCount ());
        }
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, true);
    }

    @Override
    public NodeBase add (String type, EquationTreePanel panel)
    {
        if (type.isEmpty ()  ||  type.equals ("Reference"))
        {
            // Add a new reference to our children
            int suffix = 1;
            while (source.child ("r" + suffix) != null) suffix++;
            NodeBase child = new NodeReference ((MPart) source.set ("", "r" + suffix));
            panel.model.insertNodeInto (child, this, getChildCount ());
            return child;
        }
        else
        {
            return ((NodeBase) getParent ()).add (type, panel);
        }
    }

    @Override
    public boolean allowEdit ()
    {
        return false;
    }
}
