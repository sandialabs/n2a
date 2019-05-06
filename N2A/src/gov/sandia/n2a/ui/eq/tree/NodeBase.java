/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.EquationTreeCellRenderer;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

@SuppressWarnings("serial")
public class NodeBase extends DefaultMutableTreeNode
{
    public MPart source;

    public static final int INHERIT  = 0;
    public static final int OVERRIDE = 1;
    public static final int KILL     = 2;


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

    public void hide (NodeBase child, FilteredTreeModel model, boolean notifyListeners)
    {
        int[] removedIndices = new int[1];
        removedIndices[0] = getIndexFiltered (child);
        if (removedIndices[0] == -1) return;  // child is absent or already not visible, so nothing more to do

        removeFiltered (removedIndices[0], false);
        for (Object c : children) ((NodeBase) c).invalidateTabs ();

        if (notifyListeners)
        {
            Object[] removedNodes = new Object[1];
            removedNodes[0] = child;
            model.nodesWereRemoved (this, removedIndices, removedNodes);
        }
    }

    public void unhide (NodeBase child, FilteredTreeModel model, boolean notifyListeners)
    {
        int childIndex = getIndex (child);
        if (childIndex == -1) return;

        // Determine filtered index
        int filteredIndex;
        List<Integer> filtered = getFiltered ();
        if (filtered == null) return;  // all are visible, so nothing to do
        int count = filtered.size ();
        for (filteredIndex = 0; filteredIndex < count; filteredIndex++)
        {
            int f = filtered.get (filteredIndex).intValue ();
            if (f == childIndex) return;  // already visible
            if (f >  childIndex) break;
        }

        insertFiltered (filteredIndex, childIndex, false);
        child.filter (FilteredTreeModel.filterLevel);
        for (Object c : children) ((NodeBase) c).invalidateTabs ();

        if (notifyListeners)
        {
            int[] filteredIndices = new int[1];
            filteredIndices[0] = filteredIndex;
            model.nodesWereInserted (this, filteredIndices);
        }
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
        Update "filtered" list with newly inserted or unhidden child node.
        @param filteredIndex The index in "filtered" of the newly inserted node. -1 if not visible
        @param childrenIndex The index in "children" of the newly inserted node.
        @param shift Indicates that a new child node was actually added (by caller), so indices should be upshifted.
    **/
    public void insertFiltered (int filteredIndex, int childrenIndex, boolean shift)
    {
    }

    /**
        Update "filtered" list for removed or hidden child node.
        @param filteredIndex The index in "filtered" of the node before it was removed.
        @param shift Indicates that the child node was actually removed (by caller), so indices should be downshifted.
    **/
    public void removeFiltered (int filteredIndex, boolean shift)
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

    public Enumeration<?> childrenFiltered ()
    {
        final List<Integer> filtered = getFiltered ();
        if (filtered == null) return children ();
        return new Enumeration<Object> ()
        {
            int i;

            public boolean hasMoreElements ()
            {
                return i < filtered.size ();
            }

            public Object nextElement ()
            {
                return children.get (filtered.get (i++));  // Note the post-increment.
            }
        };
    }

    // Appearance in tree ----------------------------------------------------

    public Icon getIcon (boolean expanded)
    {
        return null;
    }

    /**
        @param editing When true, the returned text should be suitable for editing.
        Tabs should be removed for ease of navigation.
    **/
    public String getText (boolean expanded, boolean editing)
    {
        return toString ();  // parent class uses the "user object", which is the string we set elsewhere
    }

    public int getForegroundColor ()
    {
        if (source.isFromTopDocument ()) return OVERRIDE;
        return                                  INHERIT;
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
        for (int i = 0; i < count; i++) tabs.set (i, sum += tabs.get (i));

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
        If the answer is true, then initTabs() is called with a properly contextualized FontMetrics.
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
        The tabbed version of the text may be stored as the user object, because getText() will be
        called to obtain the text for editing.
        @param tabs The first column always starts at 0, so 0 is not included in the list of tab stops.
    **/
    public void applyTabStops (List<Integer> tabs, FontMetrics fm)
    {
    }

    public static String pad (String result, int offset, FontMetrics fm)
    {
        // Full-width spaces
        int length = fm.stringWidth (result);
        while (length < offset)
        {
            String next = result + " ";
            int nextLength = fm.stringWidth (next);
            if (nextLength > offset) break;
            result = next;
            length = nextLength;
        }
        // Hairline spaces
        while (length < offset)
        {
            String next = result + "\u200A";
            int nextLength = fm.stringWidth (next);
            if (nextLength > offset) break;
            result = next;
            length = nextLength;
        }
        return result;
    }

    public FontMetrics getFontMetrics (JTree tree)
    {
        EquationTreeCellRenderer renderer = (EquationTreeCellRenderer) tree.getCellRenderer ();
        Font f = renderer.getFontFor (tree, this);
        return renderer.getFontMetrics (f);
    }

    // Copy/Drag -------------------------------------------------------------

    public String getTypeName ()
    {
        Class<? extends NodeBase> c = getClass ();
        return c.getSimpleName ().substring (4);  // remove "Node"
    }

    /**
        Assemble the visible subtree, starting at this node, and return it as a child of the given MNode.
    **/
    public void copy (MNode result)
    {
        MNode n = result.set (source.get (), source.key ());
        Enumeration<?> cf = childrenFiltered ();
        while (cf.hasMoreElements ()) ((NodeBase) cf.nextElement ()).copy (n);
    }

    // Structure maintenance -------------------------------------------------

    public PanelEquationTree getTree ()
    {
        return ((NodeBase) parent).getTree ();  // Should always lead to a NodePart
    }

    public NodeBase getTrueParent ()
    {
        return (NodeBase) parent;
    }

    public List<String> getKeyPath ()
    {
        List<String> result = new ArrayList<String> ();
        NodeBase p = this;
        while (p != null)
        {
            result.add (0, p.source.key ());
            p = p.getTrueParent ();
        }
        return result;
    }

    /**
        Locates original node using given absolute path.
        Assumes that correct document has been loaded (and focused) via PanelEquations.StoredView.
    **/
    public static NodeBase locateNode (List<String> path)
    {
        NodeBase result = PanelModel.instance.panelEquations.root;
        for (int i = 1; i < path.size (); i++)  // The first entry in the path is the document name (name of root node itself). We no longer use this value, since StoredView handles document loading now.
        {
            result = (NodeBase) result.child (path.get (i));  // not filtered, because we are concerned with maintaining the model, not the view
            if (result == null) break;
        }
        return result;  // Can return null, if the leaf node is not found.
    }

    public NodeBase child (String key)
    {
        if (children == null) return null;
        for (Object o : children)
        {
            NodeBase n = (NodeBase) o;
            if (n.source.key ().equals (key)) return n;
        }
        return null;
    }

    public NodeBase childFiltered (String key)
    {
        if (children == null) return null;
        List<Integer> filtered = getFiltered ();
        if (filtered == null) return child (key);
        for (int i : filtered)
        {
            NodeBase n = (NodeBase) children.get (i);
            if (n.source.key ().equals (key)) return n;
        }
        return null;
    }

    public NodeBase add (String type, JTree tree, MNode data, Point location)
    {
        return ((NodeBase) parent).add (type, tree, data, location);  // default action is to refer the add request up the tree
    }

    public boolean allowEdit ()
    {
        return true;  // Most nodes are editable. Must specifically block editing.
    }

    public void applyEdit (JTree tree)
    {
        System.out.println ("NodeBase.applyEdit: " + this);
    }

    public void delete (JTree tree, boolean canceled)
    {
        // Default action is to ignore request. Only nodes that can actually be deleted need to override this.
    }
}
