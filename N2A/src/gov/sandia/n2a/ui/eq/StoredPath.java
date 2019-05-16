/*
Copyright 2016-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreePath;

import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class StoredPath
{
    boolean        open;     // Was root node expanded?
    boolean        selected; // Was anything selected?
    boolean        expanded; // Was the selected node also expanded?
    List<String>   keys   = new ArrayList<String> ();   // of the selected node
    List<String[]> others = new ArrayList<String[]> (); // All the tree nodes that were expanded before. May include the current selection. These nodes get less detailed processing.

    public StoredPath (JTree tree)
    {
        open = tree.isExpanded (0);

        TreePath path = tree.getSelectionPath ();
        selected = path != null;
        if (selected)
        {
            for (Object o : path.getPath ()) keys.add (((NodeBase) o).source.key ());
            keys.remove (0);  // don't need to store root
            expanded = tree.isExpanded (path);
        }

        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        NodeBase n = (NodeBase) model.getRoot ();
        Enumeration<TreePath> expandedNodes = tree.getExpandedDescendants (new TreePath (n.getPath ()));
        if (expandedNodes == null) return;
        while (expandedNodes.hasMoreElements ())
        {
            path = expandedNodes.nextElement ();
            Object[] objectPath = path.getPath ();
            if (objectPath.length == 1) continue;  // Don't store root node, because we handle its state via "open".
            String[] stringPath = new String[objectPath.length - 1];
            for (int i = 1; i < objectPath.length; i++) stringPath[i-1] = ((NodeBase) objectPath[i]).source.key ();
            others.add (stringPath);
        }
    }

    public void restore (JTree tree)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        NodeBase n = (NodeBase) model.getRoot ();  // goal is to find n closest to original selected node as possible

        // First restore all previously expanded nodes
        for (String[] stringPath : others)
        {
            NodeBase c = n;
            for (String key : stringPath)
            {
                c = c.child (key);
                if (c == null) break;
            }
            if (c != null  &&  c.visible (FilteredTreeModel.filterLevel)) tree.expandPath (new TreePath (c.getPath ()));
        }

        if (open) tree.expandRow (0);
        else      tree.collapseRow (0);

        // Second, locate the focused node and pay special attention to its visibility
        if (! selected)
        {
            tree.clearSelection ();
            return;
        }
        for (String key : keys)
        {
            int childCount = n.getChildCount ();
            int i;
            for (i = 0; i < childCount; i++)
            {
                NodeBase c = (NodeBase) n.getChildAt (i);
                if (c.source.key ().equals (key))
                {
                    n = c;
                    break;
                }
            }
            if (i >= childCount)  // The key was not found at all. n remains at parent node
            {
                expanded = true;  // Always expand the parent when a child is lost.
                break;
            }

            if (! n.visible (FilteredTreeModel.filterLevel))  // The node we actually found is currently filtered out, so find nearest sibling
            {
                n = (NodeBase) n.getParent ();  // If nothing is found, n will remain at the parent.

                // First walk forward
                boolean found = false;
                for (int j = i + 1; j < childCount; j++)
                {
                    NodeBase c = (NodeBase) n.getChildAt (j);
                    if (c.visible (FilteredTreeModel.filterLevel))
                    {
                        n = c;
                        found = true;
                        break;
                    }
                }
                if (found) break;

                // Then if needed, walk backward.
                for (int j = i - 1; j >= 0; j--)
                {
                    NodeBase c = (NodeBase) n.getChildAt (j);
                    if (c.visible (FilteredTreeModel.filterLevel))
                    {
                        n = c;
                        found = true;
                        break;
                    }
                }
                if (! found) expanded = true;
                break;
            }
        }
        TreePath path = new TreePath (n.getPath ());
        tree.setSelectionPath (path);
        if (expanded) tree.expandPath (path);
        tree.scrollPathToVisible (path);
    }

    public String toString ()
    {
        return keys.toString ();
    }
}
