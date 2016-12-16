/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.eqset.MPart;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class NodeBase extends DefaultMutableTreeNode
{
    public MPart source;

    // Filtering -------------------------------------------------------------

    /**
        Indicates whether this node is visible under the given criteria.
    **/
    public boolean visible (int filterLevel)
    {
        return true;
    }

    /**
        Prepares this node and all its descendants to answer the getFiltered() call
        for a given filter level.
    **/
    public void filter (int filterLevel)
    {
    }

    public void hide (NodeBase child, FilteredTreeModel model)
    {
        int[]    removedIndices = new int   [1];
        Object[] removedNodes   = new Object[1];
        removedIndices[0] = getIndexFiltered (child);
        removedNodes  [0] = child;

        filter (model.filterLevel);  // Not the most efficient way to remove child, but simple. Also resets column spacing.
        model.nodesWereRemoved (this, removedIndices, removedNodes);
    }

    /**
        @return List of child indices, in order, that are currently visible in tree.
        null indicates no filtering, and thus all nodes are visible.
    **/
    public List<Integer> getFiltered ()
    {
        return null;
    }

    /**
        Update "filtered" list with newly inserted child node.
        @param filteredIndex The index in "filtered" of the newly inserted node. -1 if not visible
        @param childrenIndex The index in "children" of the newly inserted node.
    **/
    public void insertFiltered (int filteredIndex, int childrenIndex)
    {
    }

    /**
        Update "filtered" list for removed child node.
        @param filteredIndex The index in "filtered" of the node before it was removed.
    **/
    public void removeFiltered (int filteredIndex)
    {
    }

    public int getIndexFiltered (TreeNode child)
    {
        int result = children.indexOf (child);
        List<Integer> filtered = getFiltered ();
        if (filtered != null) result = filtered.indexOf (result);
        return result;
    }

    public TreeNode getChildAtFiltered (int index)
    {
        List<Integer> filtered = getFiltered ();
        if (filtered != null) index = filtered.get (index).intValue ();
        return (TreeNode) children.get (index);
    }

    // Appearance in tree ----------------------------------------------------

    public Icon getIcon (boolean expanded)
    {
        return null;
    }

    public String getText (boolean expanded, boolean editing)
    {
        return toString ();  // parent class uses the "user object", which is the string we set elsewhere
    }

    public Color getForegroundColor ()
    {
        if (source.isFromTopDocument ()) return (Color.black);
        return                                   Color.blue;
    }

    /**
        Returns relative scaling of font w.r.t. size used in most of the tree.
    **/
    public float getFontScale ()
    {
        return 1;
    }

    public int getFontStyle ()
    {
        return Font.PLAIN;
    }

    // Column alignment ------------------------------------------------------

    /**
        Combines column width information from children to generate a set of tab stops that all children should use when displaying text.
    **/
    public void updateTabStops (FontMetrics fm)
    {
        if (children == null) return;
        List<Integer> filtered = getFiltered ();
        if (filtered == null)
        {
            int count = children.size ();
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }

        ArrayList<Integer> tabs = new ArrayList<Integer> ();
        for (int index : filtered)
        {
            List<Integer> columnWidths = ((NodeBase) children.get (index)).getColumnWidths ();
            if (columnWidths == null) continue;

            int i = 0;
            int columns = columnWidths.size ();
            int overlap = Math.min (columns, tabs.size ());
            for (; i < overlap; i++) tabs.set (i, Math.max (columnWidths.get (i), tabs.get (i)));
            for (; i < columns; i++) tabs.add (columnWidths.get (i));
        }
        int count = tabs.size ();
        if (count == 0) return;

        int sum = 0;
        for (int i = 0; i < count; i++) tabs.set (i, sum += tabs.get (i).intValue ());

        for (int index : filtered) ((NodeBase) children.get (index)).applyTabStops (tabs, fm);
    }

    /**
        Call DefaultModel.nodesChanged for all children of the current node.
        Normally done right after a call to updateTabStops(). However, this
        function can't be combined with that one, because it breaks initialization.
    **/
    public void allNodesChanged (FilteredTreeModel model)
    {
        int count;
        List<Integer> filtered = getFiltered ();
        if (filtered == null) count = children.size ();
        else                  count = filtered.size ();
        int[] childIndices = new int[count];
        for (int i = 0; i < count; i++) childIndices[i] = i;
        model.nodesChanged (this, childIndices);
    }

    /**
        Set this node that it will return true on a call to needsInitTabs().
        This only affects nodes which actually do tab initialization.
        Called by filter() on child nodes so that they will recompute column
        alignment in the context of a filtered set of siblings.
    **/
    public void invalidateTabs ()
    {
    }

    /**
        Check if this node uses tab stops, and if so, whether they need to be initialized.
        This is called every time the node is about to be rendered, and ideally should answer true only once.
        If the answer is true, then updateSiblingColumnWidths() is called with a properly contextualized FontMetrics.
    **/
    public boolean needsInitTabs ()
    {
        return false;
    }

    /**
        Do the full process of setting up tab stops on all the siblings of this node (including itself).
    **/
    public void initTabs (FontMetrics fm)
    {
        NodeBase parent = (NodeBase) getParent ();
        if (parent == null) return;
        for (Object c : parent.children) ((NodeBase) c).updateColumnWidths (fm);
        parent.updateTabStops (fm);
    }

    /**
        Determines the column widths that will be returned by getColumnWidths().
        Presumably these are cached in some form, as the getColumnWidth() call may occur multiple times without an intervening update.
    **/
    public void updateColumnWidths (FontMetrics fm)
    {
    }

    /**
        Provides container with the widths of various components of the displayed text.
        For example, an equation could have up to 4 components: variable, assignment, value, condition.
    **/
    public List<Integer> getColumnWidths ()
    {
        return null;
    }

    /**
        Prepare to efficiently respond to getText() with a modified value that produces column alignment.
        This might involve inserting space or tab characters into a cached version of the text.
        Note that the tabbed version of the text is not stored as the user object, because that is used
        primarily for editing, and in edit mode tabs should be avoided to reduce navigation.
        @param tabs The first column always starts at 0, so 0 is not included in the list of tab stops.
    **/
    public void applyTabStops (List<Integer> tabs, FontMetrics fm)
    {
    }

    public static String pad (int offset, FontMetrics fm)
    {
        String result = "";
        int space = fm.charWidth (' ');
        while (offset > space)
        {
            result += " ";
            offset -= space;
        }
        if (offset > 0)
        {
            space = fm.charWidth (0x200A);  // hairline space
            while (offset > space)
            {
                result += "\u200A";
                offset -= space;
            }
        }
        return result;
    }

    public FontMetrics getFontMetrics (JTree tree)
    {
        EquationTreeCellRenderer renderer = (EquationTreeCellRenderer) tree.getCellRenderer ();
        Font f = renderer.getFontFor (this);
        return tree.getGraphics ().getFontMetrics (f);
    }

    // Structure maintenance -------------------------------------------------

    public List<String> getKeyPath ()
    {
        TreeNode[] path = getPath ();
        List<String> result = new ArrayList<String> (path.length);
        for (TreeNode n : path) result.add (((NodeBase) n).source.key ());
        return result;
    }

    public NodeBase child (String key)
    {
        Enumeration i = children ();
        while (i.hasMoreElements ())
        {
            NodeBase n = (NodeBase) i.nextElement ();
            if (n.source.key ().equals (key)) return n;
        }
        return null;
    }

    public NodeBase childFiltered (String key)
    {
        List<Integer> filtered = getFiltered ();
        if (filtered == null) return child (key);
        for (int i : filtered)
        {
            NodeBase n = (NodeBase) children.get (i);
            if (n.source.key ().equals (key)) return n;
        }
        return null;
    }

    public NodeBase add (String type, JTree tree)
    {
        return ((NodeBase) getParent ()).add (type, tree);  // default action is to refer the add request up the tree
    }

    public NodeBase addDnD (String key, JTree tree)
    {
        return ((NodeBase) getParent ()).addDnD (key, tree);
    }

    public boolean allowEdit ()
    {
        return true;  // Most nodes are editable. Must specifically block editing.
    }

    public void applyEdit (JTree tree)
    {
        System.out.println ("NodeBase.applyEdit: " + this);
    }

    public void delete (JTree tree)
    {
        // Default action is to ignore request. Only nodes that can actually be deleted need to override this.
    }
}
