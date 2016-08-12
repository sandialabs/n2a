/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.umf.platform.db.MPersistent;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;

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
            root.source = MPart.collate (doc);
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
