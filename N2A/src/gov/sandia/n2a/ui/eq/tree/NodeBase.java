/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.db.MPersistent;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class NodeBase extends DefaultMutableTreeNode
{
    public MPart source;

    public Icon getIcon (boolean expanded)
    {
        return null;
    }

    public String getText (boolean expanded)
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

    /**
        Combines column width information from children to generate a set of tab stops that all children should use when displaying text.
    **/
    public void updateTabStops (FontMetrics fm)
    {
        ArrayList<Integer> tabs = new ArrayList<Integer> ();
        for (Object c : children)
        {
            List<Integer> columnWidths = ((NodeBase) c).getColumnWidths ();
            if (columnWidths == null) continue;

            int i = 0;
            int columns = columnWidths.size ();
            int overlap = Math.min (columns, tabs.size ());
            for (; i < overlap; i++) tabs.set (i, Math.max (columnWidths.get (i), tabs.get (i)));
            for (; i < columns; i++) tabs.add (columnWidths.get (i));
        }
        if (tabs.size () == 0) return;

        int sum = 0;
        Iterator<Integer> i = tabs.iterator ();
        while (i.hasNext ())
        {
            Integer c = i.next ();
            c = sum += c.intValue ();
        }

        for (Object c : children) ((NodeBase) c).applyTabStops (tabs, fm);
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
        EquationTreePanel.NodeRenderer renderer = (EquationTreePanel.NodeRenderer) tree.getCellRenderer ();
        Font f = renderer.getFontFor (this);
        return tree.getGraphics ().getFontMetrics (f);
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

    /**
        General utility to completely rebuild node tree and re-display.
    **/
    public void reloadTree (final JTree tree)
    {
        final NodePart root = (NodePart) getRoot ();
        MPersistent doc = root.source.getSource ();

        final ArrayList<String> keyPath = new ArrayList<String> ();
        for (TreeNode t : getPath ()) keyPath.add (((NodeBase) t).source.key ());

        try
        {
            root.source = new MPart (doc);
            root.build ();
            root.findConnections ();
            ((DefaultTreeModel) tree.getModel ()).reload ();

            // Re-select the current node, or as close as possible.
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    NodeBase n = root;
                    for (int i = 1; i < keyPath.size (); i++)
                    {
                        String key = keyPath.get (i);
                        Enumeration e = n.children ();
                        boolean found = false;
                        while (e.hasMoreElements ())
                        {
                            NodeBase c = (NodeBase) e.nextElement ();
                            if (c.source.key ().equals (key))
                            {
                                found = true;
                                n = c;
                                break;
                            }
                        }
                        if (! found) break;
                    }
                    tree.setSelectionPath (new TreePath (n.getPath ()));
                }
            });
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
        }
    }
}
