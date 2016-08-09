/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

public class NodeInherit extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("inherit.png");

    public NodeInherit (MPart source)
    {
        this.source = source;
        setUserObject (source.key () + "=" + source.get ());
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
    }

    @Override
    public boolean allowEdit ()
    {
        return ((DefaultMutableTreeNode) getParent ()).isRoot ();
    }

    @Override
    public void applyEdit (DefaultTreeModel model)
    {
        String input = (String) getUserObject ();
        String[] parts = input.split ("=", 2);
        String value;
        if (parts.length > 1) value = parts[1];
        else                  value = "";

        String oldValue = source.key ();
        if (value.equals (oldValue)) return;
        source.set (value);

        NodePart root = (NodePart) getParent ();  // guaranteed by our allowEdit() method
        MPersistent doc = root.source.getSource ();
        try
        {
            root.source = MPart.collate (doc);
            root.build ();
            root.findConnections ();
            model.reload ();
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
        }
    }
}
