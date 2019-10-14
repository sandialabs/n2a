/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.ui.eq.tree.NodeBase;
import java.util.List;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

@SuppressWarnings("serial")
public class FilteredTreeModel extends DefaultTreeModel
{
    public static final int REVOKED = -1;
    public static final int ALL     = 0;
    public static final int PARAM   = 1;
    public static final int LOCAL   = 2;
    public static int filterLevel = ALL;  // Only one filter level shared across entire app.

    public FilteredTreeModel (NodeBase root)
    {
        // initial state is unfiltered
        super (root);
    }

    public void setRoot (NodeBase root)
    {
        if (root != null) root.filter (filterLevel);
        super.setRoot (root);
    }

    /**
        Nothing about this method requires it to be instance-specific,
        other than the fact that the interface is written that way.
    **/
    public int getIndexOfChild (Object parent, Object child)
    {
        return getIndexOfChildStatic (parent, child);
    }

    public static int getIndexOfChildStatic (Object parent, Object child)
    {
        if (parent == null  ||  child == null) return -1;
        return ((NodeBase) parent).getIndexFiltered ((TreeNode) child);
    }

    public Object getChild (Object parent, int filteredIndex)
    {
        return ((NodeBase) parent).getChildAtFiltered (filteredIndex);
    }

    public int getChildCount (Object parent)
    {
        NodeBase p = (NodeBase) parent;
        List<Integer> filtered = p.getFiltered ();
        if (filtered == null) return p.getChildCount ();
        return filtered.size ();
    }

    public boolean isLeaf (Object node)
    {
        // Don't bother with getAllowsChildren()
        NodeBase p = (NodeBase) node;
        List<Integer> filtered = p.getFiltered ();
        if (filtered == null) return p.isLeaf ();
        return filtered.size () == 0;
    }

    public void insertNodeIntoUnfiltered (MutableTreeNode newChild, MutableTreeNode parent, int childrenIndex)
    {
        int filteredIndex = insertNodeIntoUnfilteredStatic (newChild, parent, childrenIndex);
        if (filteredIndex < 0) return;

        int[] filteredIndices = new int[1];
        filteredIndices[0] = filteredIndex;
        nodesWereInserted (parent, filteredIndices);
    }

    public static int insertNodeIntoUnfilteredStatic (MutableTreeNode newChild, MutableTreeNode parent, int childrenIndex)
    {
        NodeBase p = (NodeBase) parent;
        NodeBase c = (NodeBase) newChild;

        p.insert (c, childrenIndex);
        c.filter (filterLevel);  // If c brings a new subtree, ensure it is filtered.
        if (! c.visible (filterLevel))  // Don't send any event
        {
            p.insertFiltered (-1, childrenIndex, true);
            return -1;  // Indicate early-out
        }

        // Determine filtered index
        int filteredIndex;
        List<Integer> filtered = p.getFiltered ();
        if (filtered == null)
        {
            filteredIndex = childrenIndex;
        }
        else
        {
            int count = filtered.size ();
            for (filteredIndex = 0; filteredIndex < count; filteredIndex++)
            {
                if (filtered.get (filteredIndex).intValue () >= childrenIndex) break;
            }
            p.insertFiltered (filteredIndex, childrenIndex, true);
        }

        return filteredIndex;
    }

    public void insertNodeInto (MutableTreeNode newChild, MutableTreeNode parent, int filteredIndex)
    {
        NodeBase p = (NodeBase) parent;
        NodeBase c = (NodeBase) newChild;

        int childrenIndex;
        List<Integer> filtered = p.getFiltered ();
        if (filtered == null)                      childrenIndex = filteredIndex;
        else if (filteredIndex < filtered.size ()) childrenIndex = filtered.get (filteredIndex).intValue ();
        else                                       childrenIndex = p.getChildCount ();
        p.insert (c, childrenIndex);

        c.filter (filterLevel);
        if (! c.visible (filterLevel))
        {
            p.insertFiltered (-1, childrenIndex, true);
            return;
        }
        p.insertFiltered (filteredIndex, childrenIndex, true);

        int[] filteredIndices = new int[1];
        filteredIndices[0] = filteredIndex;
        nodesWereInserted (parent, filteredIndices);
    }

    public void removeNodeFromParent (MutableTreeNode child)
    {
        NodeBase parent = ((NodeBase) child).getTrueParent ();
        int filteredIndex = removeNodeFromParentStatic (parent, child);
        if (filteredIndex < 0) return;  // No need to send event, because this node was not visible.

        int[]    removedIndices = new int   [1];
        Object[] removedObjects = new Object[1];
        removedIndices[0] = filteredIndex;
        removedObjects[0] = child;
        nodesWereRemoved (parent, removedIndices, removedObjects);
    }

    public static int removeNodeFromParentStatic (MutableTreeNode child)
    {
        NodeBase parent = ((NodeBase) child).getTrueParent ();
        return removeNodeFromParentStatic (parent, child);
    }

    public static int removeNodeFromParentStatic (NodeBase parent, MutableTreeNode child)
    {
        if (parent == null) throw new IllegalArgumentException ("node does not have a parent.");

        int filteredIndex = parent.getIndexFiltered ((NodeBase) child);
        int index         = parent.getIndex (child);
        parent.remove (index);  // Can't call parent.remove(child), because that function does an isNodeChild() test, which fails for fake roots.
        parent.removeFiltered (filteredIndex, index, true);
        return filteredIndex;
    }

    public void nodeChanged (TreeNode node)
    {
        if (listenerList == null  ||  node == null) return;

        NodeBase parent = (NodeBase) node.getParent ();
        if (parent != null)
        {
            int filteredIndex = parent.getIndexFiltered (node);
            if (filteredIndex >= 0)
            {
                int[] changedIndices = new int[1];
                changedIndices[0] = filteredIndex;
                nodesChanged (parent, changedIndices);
            }
        }
        else if (node == getRoot ())
        {
            nodesChanged (node, null);
        }
    }

    public void nodesWereInserted (TreeNode node, int[] childIndices)
    {
        if (listenerList != null  &&  node != null  &&  childIndices != null  &&  childIndices.length > 0)
        {
            int count = childIndices.length;
            Object[] newChildren = new Object[count];
            for (int i = 0; i < count; i++) newChildren[i] = ((NodeBase) node).getChildAtFiltered (childIndices[i]);
            fireTreeNodesInserted (this, getPathToRoot (node), childIndices, newChildren);
        }
    }

    public void nodesChanged (TreeNode node, int[] childIndices)
    {
        if (node == null) return;
        if (childIndices != null)
        {
            int count = childIndices.length;
            if (count > 0)
            {
                Object[] childObjects = new Object[count];
                for (int i = 0; i < count; i++) childObjects[i] = ((NodeBase) node).getChildAtFiltered (childIndices[i]);
                fireTreeNodesChanged (this, getPathToRoot (node), childIndices, childObjects);
            }
        }
        else if (node == getRoot ())
        {
            fireTreeNodesChanged (this, getPathToRoot (node), null, null);
        }
    }

}
