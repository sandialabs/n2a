/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

public class NodeReferences extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeReferences ()
    {
        setUserObject ("$reference");
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, true);
    }

    @Override
    public void add (String type, EquationTreePanel panel)
    {
        if (type.isEmpty ()  ||  type.equals ("Reference"))
        {
            // Add a new reference to our children
            NodeBase child = new NodeReference ("", "");
            panel.model.insertNodeInto (child, this, getChildCount ());
            TreePath path = new TreePath (child.getPath ());
            panel.tree.scrollPathToVisible (path);  // TODO: is this really necessary? IE: does startEditingAtPath() take care of this for us?
            panel.tree.startEditingAtPath (path);
        }
        else
        {
            ((NodeBase) getParent ()).add (type, panel);
        }
    }
}
