/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

public class NodeAnnotations extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeAnnotations (MPart source)
    {
        this.source = source;
        setUserObject ("$metadata");
    }

    public void build ()
    {
        for (MNode c : source) add (new NodeAnnotation ((MPart) c));
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, true);
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()  ||  type.equals ("Annotation"))
        {
            // Add a new annotation to our children
            int suffix = 1;
            while (source.child ("a" + suffix) != null) suffix++;
            NodeBase result = new NodeAnnotation ((MPart) source.set ("", "a" + suffix));
            ((DefaultTreeModel) tree.getModel ()).insertNodeInto (result, this, getChildCount ());
            result.setUserObject ("");
            return result;
        }
        else
        {
            return ((NodeBase) getParent ()).add (type, tree);
        }
    }

    @Override
    public boolean allowEdit ()
    {
        return false;
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;

        MPart mparent = source.getParent ();
        String key = source.key ();  // "$metadata"
        mparent.clear (key);
        if (mparent.child (key) == null) ((DefaultTreeModel) tree.getModel ()).removeNodeFromParent (this);
    }
}
