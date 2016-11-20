/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Font;
import java.awt.FontMetrics;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class NodeReferences extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeReferences (MPart source)
    {
        this.source = source;
        setUserObject ("$reference");
    }

    public void build ()
    {
        for (MNode c : source) add (new NodeReference ((MPart) c));
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel == FilteredTreeModel.ALL)    return true;
        if (filterLevel == FilteredTreeModel.PUBLIC) return true;
        // FilteredTreeModel.LOCAL ...
        return source.isFromTopDocument ();
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (expanded) return icon;
        return NodeReference.icon;
    }

    @Override
    public int getFontStyle ()
    {
        return Font.ITALIC;
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()  ||  type.equals ("Reference"))
        {
            // Add a new reference to our children
            int suffix = 1;
            while (source.child ("r" + suffix) != null) suffix++;
            NodeBase result = new NodeReference ((MPart) source.set ("", "r" + suffix));

            int selectedIndex = getChildCount () - 1;
            TreePath path = tree.getSelectionPath ();
            if (path != null)
            {
                NodeBase selected = (NodeBase) path.getLastPathComponent ();
                if (isNodeChild (selected)) selectedIndex = getIndex (selected);  // unfiltered index
            }

            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            FontMetrics fm = getFontMetrics (tree);
            if (children != null  &&  children.size () > 0)
            {
                NodeBase firstChild = (NodeBase) children.get (0);
                if (firstChild.needsInitTabs ()) firstChild.initTabs (fm);
            }

            result.setUserObject ("");
            result.updateColumnWidths (fm);  // preempt initialization
            model.insertNodeIntoUnfiltered (result, this, selectedIndex + 1);

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

        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        MPart mparent = source.getParent ();
        String key = source.key ();  // "$reference"
        mparent.clear (key);
        if (mparent.child (key) == null)
        {
            model.removeNodeFromParent (this);
        }
        else  // Just exposed an overridden node
        {
            if (! visible (model.filterLevel)) ((NodeBase) getParent ()).hide (this, model);
            // If we are visible, we need to change color, but not necessary to do it here, because it is handled by EquationTreePanel.updateOverrides().
        }
    }
}
