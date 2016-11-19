/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class StoredPath
{
    List<String> keys = new ArrayList<String> ();
    boolean expanded;  ///< Indicates that the selected node was open, so we need to open it again on restore.

    public StoredPath (JTree tree)
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;
        for (Object o : path.getPath ()) keys.add (((NodeBase) o).source.key ());
        keys.remove (0);  // don't need to store root
        expanded = tree.isExpanded (path);
    }

    public void restore (JTree tree)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        NodeBase n = (NodeBase) model.getRoot ();  // goal is to find n closest to original selected node as possible

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

            if (! n.visible (model.filterLevel))  // The node we actually found is currently filtered out, so find nearest sibling
            {
                n = (NodeBase) n.getParent ();  // If nothing is found, n will remain at the parent.

                // First walk forward
                boolean found = false;
                for (int j = i + 1; j < childCount; j++)
                {
                    NodeBase c = (NodeBase) n.getChildAt (j);
                    if (c.visible (model.filterLevel))
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
                    if (c.visible (model.filterLevel))
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
    }

    public String toString ()
    {
        return keys.toString ();
    }
}
