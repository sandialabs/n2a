/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddEditable;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeBase extends DefaultMutableTreeNode
{
    public MPart source;

    public static final int INHERIT  = 0;
    public static final int OVERRIDE = 1;
    public static final int KILL     = 2;


    public void setUserObject ()
    {
        setUserObject (source.key () + "=" + source.get ());
    }

    // Filtering -------------------------------------------------------------

    /**
        Indicates whether this node is visible under the given criteria.
    **/
    public boolean visible ()
    {
        boolean revoked = isRevoked ();
        if (FilteredTreeModel.showParam) return isParam ()  &&  ! revoked;
        if (revoked)    return FilteredTreeModel.showRevoked;
        if (isLocal ()) return FilteredTreeModel.showLocal;
        else            return FilteredTreeModel.showInherited;
    }

    public boolean isRevoked ()
    {
        return source.getFlag ("$kill");
    }

    public boolean isLocal ()
    {
        return source.isFromTopDocument ();
    }

    public boolean isParam ()
    {
        return source.getFlag ("$meta", "param");
    }

    /**
        Prepares this node and all its descendants to answer the getFiltered() call
        for a given filter level.
    **/
    public void filter ()
    {
    }

    public void hide (NodeBase child, FilteredTreeModel model)
    {
        int removedIndex = getIndexFiltered (child);
        if (removedIndex == -1) return;  // child is absent or already not visible, so nothing more to do

        removeFiltered (removedIndex, -1, false);

        if (model != null)
        {
            int[] removedIndices = new int[1];
            removedIndices[0] = removedIndex;
            Object[] removedNodes = new Object[1];
            removedNodes[0] = child;
            model.nodesWereRemoved (this, removedIndices, removedNodes);
        }

        invalidateColumns (model);
    }

    public void unhide (NodeBase child, FilteredTreeModel model)
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
        child.filter ();

        if (model != null)
        {
            int[] filteredIndices = new int[1];
            filteredIndices[0] = filteredIndex;
            model.nodesWereInserted (this, filteredIndices);
        }

        invalidateColumns (model);
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
        @param filteredIndex The index in "filtered" of the node before it was removed. -1 if it was not visible.
        @param childrenIndex The index in "children" of the node before it was removed. This value is only used when shift is true and filteredIndex is -1.
        @param shift Indicates that the child node was actually removed (by caller), so indices should be downshifted.
    **/
    public void removeFiltered (int filteredIndex, int childrenIndex, boolean shift)
    {
    }

    /**
        Must override this, because the base class does an isNodeChild() test which is fooled by fake roots.
        The answer is still correct, in that we return -1 if it is not really our child.
    **/
    public int getIndex (TreeNode child)
    {
        return children.indexOf (child);
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

    public String getToolTipText (FontMetrics fm)
    {
        return getToolTipText (source, fm);
    }

    public static String getToolTipText (MNode node, FontMetrics fm)
    {
        String                notes = node.get ("$meta", "notes");
        if (notes.isBlank ()) notes = node.get ("$meta", "note");
        if (notes.isBlank ()) notes = node.get ("$meta", "description");  // for NeuroML parts
        if (notes.isBlank ()) return null;

        int pos = notes.indexOf ('.');
        if (pos < 0) pos = notes.indexOf ('\n');
        else         pos++;  // To include '.' in truncated text.
        if (pos > 0) notes = notes.substring (0, pos);
        if (node.getFlag ("$meta", "gui", "mixin" )) notes = "[MIXIN] "  + notes;
        if (node.getFlag ("$meta", "gui", "dropin")) notes = "[DROPIN] " + notes;
        return formatToolTipText (notes, fm);
    }

    public static String formatToolTipText (String text, FontMetrics fm)
    {
        double em = SettingsLookAndFeel.em;
        int frameWidth = (int) Math.min (60 * em, MainFrame.instance.getWidth ());
        frameWidth = (int) Math.max (23 * em, frameWidth);
        int notesWidth = fm.stringWidth (text);
        if (notesWidth < frameWidth) return text;

        text = escapeHTML (text, false);
        return "<html><p width=" + frameWidth + ">" + text + "</p></html>";
    }

    public Icon getIcon (boolean expanded)
    {
        return null;
    }

    public int getForegroundColor ()
    {
        if (source.getFlag ("$kill"))    return KILL;
        if (source.isFromTopDocument ()) return OVERRIDE;
        return                                  INHERIT;
    }

    /**
        Returns a font suitable for HTML styling.
    **/
    public Font getPlainFont (Font base)
    {
        return base;
    }

    /**
        Returns a font suitable for editing node value in plain (non-HTML) text.
        Takes into account the HTML styling that would otherwise be applied.
    **/
    public Font getStyledFont (Font base)
    {
        return base;
    }

    public boolean allowTruncate ()
    {
        return false;
    }

    /**
        Used by renderer to inform this node that part of its text was cut off last time it was rendered.
    **/
    public void wasTruncated ()
    {
    }

    /**
        Indicates that tree should allow edit on this node even if the tree is locked,
        so that full contents can be viewed. If the tree is locked, the editor will be
        set to read-only mode so no harm will be done.
    **/
    public boolean showMultiLine ()
    {
        return false;
    }

    public static String escapeHTML (String value, boolean singleLine)
    {
        value = value.replace ("&",  "&amp;");
        value = value.replace ("<",  "&lt;");
        value = value.replace (">",  "&gt;");
        value = value.replace ("\"", "&quot;");
        if (! singleLine) value = value.replace ("\n", "<br>");
        return value;
    }

    public List<String> getColumns (boolean selected, boolean expanded)
    {
        List<String> result = new ArrayList<String> ();
        result.add (toString ());
        return result;
    }

    // Column alignment ------------------------------------------------------

    /**
        Indicates which set of column widths this node wants to use.
        A container may have more than one set of column widths, to support different node categories.
        The main example is metadata versus equations under a given variable. These are mixed together,
        but look better if they have their own alignments.
    **/
    public int getColumnGroup ()
    {
        return 0;
    }

    /**
        Provides container with the widths of various components of the displayed text.
        For example, an equation could have up to 4 components: variable, assignment, value, condition.
        @param fm For measuring pixel width of strings.
    **/
    public List<Integer> getColumnWidths (FontMetrics fm)
    {
        return null;
    }

    /**
        Returns a list of pixel widths that all children of this container should use when displaying text.
        The value is calculated by calling getColumnWidths() on each child and collating the results.
        The list of widths may be cached by this container until the next call to invalidateTabs().
        If this is not a container, or has only one column, then the result may be null.
        @param group Specifies the column group. See getColumnGroup()
        @param fm In case this function needs to call getColumnWidths() on children.
    **/
    public List<Integer> getMaxColumnWidths (int group, FontMetrics fm)
    {
        return null;
    }

    /**
        Marks this container as needing to regenerate its tab stops.
        One noteworthy use of this function is when the children of this container get filtered.
        In that case, the tab stops may change because set of column widths may change.
        @param model If null, then caller takes responsibility for notifying model of change.
    **/
    public void invalidateColumns (FilteredTreeModel model)
    {
        if (model != null) allNodesChanged (model);
    }

    /**
        Notifies the tree model that all our children have changed, so that they will be redrawn by the tree.
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

    // Copy/Drag -------------------------------------------------------------

    public String getTypeName ()
    {
        Class<? extends NodeBase> c = getClass ();
        return c.getSimpleName ().substring (4);  // remove "Node"
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

    public NodeBase locateNodeFromHere (List<String> path)
    {
        NodeBase result = this;
        for (int i = 0; i < path.size (); i++)
        {
            result = (NodeBase) result.child (path.get (i));
            if (result == null) break;
        }
        return result;
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

    /**
        Walks of the ancestry of this node until a selected one is found, or we reach root.
        Does not consider graph node selection, only tree node selection.
        Stops at the first fake root.
    **/
    public boolean hasSelectedAncestor (JTree tree)
    {
        TreePath tp = new TreePath (getPath ()).getParentPath ();
        while (tp != null)
        {
            if (tree.isPathSelected (tp)) return true;
            tp = tp.getParentPath ();
        }
        return false;
    }

    /**
        Locates the nearest node in the hierarchy at or above this node which can contain the given type.
        The choice logic is equivalent to add(). However, we cheat by allowing "image" as a type as well.
    **/
    public NodeBase containerFor (String type)
    {
        return ((NodeBase) parent).containerFor (type);
    }

    public NodeBase add (String type, JTree tree, MNode data, Point2D.Double location)
    {
        Undoable u = makeAdd (type, tree, data, location);
        if (u != null) MainFrame.undoManager.apply (u);
        if (u instanceof AddEditable) return ((AddEditable) u).getCreatedNode ();
        return null;
    }

    public Undoable makeAdd (String type, JTree tree, MNode data, Point2D.Double location)
    {
        return ((NodeBase) parent).makeAdd (type, tree, data, location);  // default action is to refer the add request up the tree
    }

    public boolean allowEdit ()
    {
        return true;  // Most nodes are editable. Must specifically block editing.
    }

    public void applyEdit (JTree tree)
    {
        System.out.println ("NodeBase.applyEdit: " + this);
    }

    public void delete (boolean canceled)
    {
        Undoable u = makeDelete (canceled);
        if (u != null) MainFrame.undoManager.apply (u);
    }

    public Undoable makeDelete (boolean canceled)
    {
        // Only nodes that can actually be deleted need to override this.
        return null;
    }

    public interface Visitor
    {
        /**
            @return true to continue descent. false to terminate descent along this branch.
            A value of false only prunes this branch. Sibling branches will still be visited.
        **/
        public boolean visit (NodeBase n);
    }

    public void visit (Visitor visitor)
    {
        if (! visitor.visit (this)) return;
        if (children == null) return;
        for (TreeNode t : children) ((NodeBase) t).visit (visitor);
    }
}
