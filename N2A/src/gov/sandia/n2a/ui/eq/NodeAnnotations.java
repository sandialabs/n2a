/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Font;
import java.awt.FontMetrics;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

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
    public Icon getIcon (boolean expanded)
    {
        if (expanded) return icon;
        return NodeAnnotation.icon;
    }

    @Override
    public int getFontStyle ()
    {
        return Font.ITALIC;
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

            int selectedIndex = getChildCount () - 1;
            TreePath path = tree.getSelectionPath ();
            if (path != null)
            {
                NodeBase selected = (NodeBase) path.getLastPathComponent ();
                if (isNodeChild (selected)) selectedIndex = getIndex (selected);
            }

            DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
            FontMetrics fm = getFontMetrics (tree);
            if (children != null  &&  children.size () > 0)
            {
                NodeBase firstChild = (NodeBase) children.get (0);
                if (firstChild.needsInitTabs ()) firstChild.initTabs (fm);
            }

            result.setUserObject ("");
            result.updateColumnWidths (fm);  // preempt initialization
            model.insertNodeInto (result, this, selectedIndex + 1);

            return result;
        }
        return ((NodeBase) getParent ()).add (type, tree);
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
