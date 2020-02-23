/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.settings.SettingsRepo.Delta;
import gov.sandia.n2a.ui.settings.SettingsRepo.GitWrapper;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;


@SuppressWarnings("serial")
public class PanelDiff extends JTree
{
    protected NodeDiff         root        = new NodeDiff ();
    protected DefaultTreeModel model       = new DefaultTreeModel (root);
    protected DiffRenderer     renderer    = new DiffRenderer ();
    public    UndoManager      undoManager = new UndoManager ();
    protected GitWrapper       lastGit;
    protected Delta            lastDelta;
    protected List<String>     lastSelection;

    public PanelDiff ()
    {
        setModel (model);
        setRootVisible (false);
        setExpandsSelectedPaths (true);
        setScrollsOnExpand (true);
        getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection.
        setEditable (false);
        setCellRenderer (renderer);

        InputMap inputMap = getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "delete");

        ActionMap actionMap = getActionMap ();
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                NodeDiff n = (NodeDiff) getLastSelectedPathComponent ();
                if (n != null) undoManager.add (new DeleteDiff (n));
            }
        });

        addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                restoreFocus ();
            }

            public void focusLost (FocusEvent e)
            {
                if (! e.isTemporary ()) yieldFocus ();
            }
        });
    }

    public void load (GitWrapper git, Delta delta)
    {
        if (git == lastGit  &&  lastDelta != null  &&  delta.name.equals (lastDelta.name)) return;
        lastGit       = git;
        lastDelta     = delta;
        lastSelection = null;
        MNode A = git.getOriginal (delta);
        MNode B = git.getDocument (delta);
        NodeDiff root = new NodeDiff ();
        root.buildA (A);
        root.buildB (B);
        model.setRoot (root);  // Updates tree
        for (int i = 0; i < getRowCount (); i++) expandRow (i);  // Expand all nodes completely.
    }

    /**
        Sets tree to empty.
    **/
    public void clear ()
    {
        lastGit       = null;
        lastDelta     = null;
        lastSelection = null;
        root = new NodeDiff ();
        model.setRoot (root);
    }

    public void yieldFocus ()
    {
        TreePath path = getSelectionPath ();
        if (path != null) lastSelection = ((NodeDiff) path.getLastPathComponent ()).getKeyPath ();
        // else leave lastSelection at its previous value
        clearSelection ();
    }

    public void takeFocus ()
    {
        if (isFocusOwner ()) restoreFocus ();
        else                 requestFocusInWindow ();  // Triggers focus listener, which calls restoreFocus()
    }

    public void restoreFocus ()
    {
        if (getSelectionCount () > 0) return;
        NodeDiff n = nodeFor (lastSelection);
        TreePath path;
        if (n == null) path = getPathForRow (0);
        else           path = new TreePath (n.getPath ());
        setSelectionPath (path);
        scrollPathToVisible (path);
    }

    public NodeDiff nodeFor (List<String> path)
    {
        if (path == null) return null;
        NodeDiff n = root;
        for (String key : path)
        {
            NodeDiff c = n.child (key);
            if (c == null) break;
            n = c;
        }
        if (n == root) return null;
        return n;
    }

    public static class DiffRenderer extends DefaultTreeCellRenderer
    {
        Color colorA;    // in A only
        Color colorB;    // in B only
        Color colorSame; // in both, and the same
        Color colorDiff; // in both, and different
        // Versions of the above colors for the selected cell.
        Color colorSelectedA;
        Color colorSelectedB;
        Color colorSelectedSame;
        Color colorSelectedDiff;

        public void updateUI ()
        {
            super.updateUI ();

            // Check colors to see if text is dark or light.
            Color fg = UIManager.getColor ("Tree.textForeground");
            float[] hsb = Color.RGBtoHSB (fg.getRed (), fg.getGreen (), fg.getBlue (), null);
            if (hsb[2] > 0.5)  // Light text
            {
                colorSame = new Color (0xC0C0FF);  // light blue
                colorDiff = Color.white;
                colorA    = Color.pink;
                colorB    = new Color (0xC0FFC0);  // light green
            }
            else  // Dark text
            {
                colorSame = Color.blue;
                colorDiff = Color.black;
                colorA    = Color.red;
                colorB    = Color.green.darker ();
            }

            fg = UIManager.getColor ("Tree.selectionForeground");
            Color.RGBtoHSB (fg.getRed (), fg.getGreen (), fg.getBlue (), hsb);
            if (hsb[2] > 0.5)  // Light text
            {
                colorSelectedSame = new Color (0xC0C0FF);  // light blue
                colorSelectedDiff = Color.white;
                colorSelectedA    = Color.pink;
                colorSelectedB    = new Color (0xC0FFC0);  // light green
            }
            else  // Dark text
            {
                colorSelectedSame = Color.blue;
                colorSelectedDiff = Color.black;
                colorSelectedA    = Color.red;
                colorSelectedB    = Color.green.darker ();
            }
        }

        public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
        {
            super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

            NodeDiff n = (NodeDiff) value;
            setForeground (n.getColor (selected));
            setIcon (null);

            return this;
        }
    }

    public class NodeDiff extends DefaultMutableTreeNode
    {
        String key = "";  // MNode key. Different from user object or this.toString()
        MNode A;  // value before diff
        MNode B;  // value after diff

        /**
            Fills in tree with values from A.
            Assumes the tree is currently empty.
        **/
        public void buildA (MNode A)
        {
            this.A = A;
            key = A.key ();
            for (MNode c : A)
            {
                NodeDiff n = new NodeDiff ();
                add (n);
                n.buildA (c);
            }
        }

        /**
            Fills in tree with values from B.
            Does not know whether children nodes are present or not.
            Purges subtrees that have no differences.
        **/
        public void buildB (MNode B)
        {
            this.B = B;
            if (A == null)
            {
                key = B.key ();
                for (MNode c : B)
                {
                    NodeDiff n = new NodeDiff ();
                    add (n);
                    n.buildB (c);
                }
            }
            else
            {
                for (MNode c : B)
                {
                    NodeDiff n = child (c.key ());
                    if (n == null)
                    {
                        n = new NodeDiff ();
                        add (n);
                    }
                    n.buildB (c);
                    // TODO: this test does not take into account defined vs. undefined value
                    if (n.getChildCount () == 0  &&  n.A != null  &&  n.A.get ().equals (n.B.get ()))
                    {
                        remove (n);
                    }
                }
            }
        }

        public Color getColor (boolean selected)
        {
            if (selected)
            {
                if (A == null) return renderer.colorSelectedB;
                if (B == null) return renderer.colorSelectedA;
                if (A.key ().equals (B.key ())  &&  A.get ().equals (B.get ())) return renderer.colorSelectedSame;
                return renderer.colorSelectedDiff;
            }
            else
            {
                if (A == null) return renderer.colorB;
                if (B == null) return renderer.colorA;
                if (A.key ().equals (B.key ())  &&  A.get ().equals (B.get ())) return renderer.colorSame;
                return renderer.colorDiff;
            }
        }

        public List<String> getKeyPath ()
        {
            List<String> result;
            NodeDiff parent = (NodeDiff) getParent ();
            if (parent == null)
            {
                result = new ArrayList<String> ();  // Don't include root itself.
            }
            else
            {
                result = parent.getKeyPath ();
                result.add (key);
            }
            return result;
        }

        public NodeDiff child (String key)
        {
            if (children == null) return null;
            for (Object o : children)
            {
                NodeDiff n = (NodeDiff) o;
                if (n.key.equals (key)) return (NodeDiff) o;
            }
            return null;
        }

        public String toString ()
        {
            if (userObject == null)
            {
                userObject = key;
                if (B != null)
                {
                    String value = B.get ();
                    if (! value.isEmpty ()) userObject += " = " + value;
                }
                else if (A != null)
                {
                    String value = A.get ();
                    if (! value.isEmpty ()) userObject += " = " + value;
                }
            }
            return userObject.toString ();
        }

        public void dump (String indent)
        {
            System.out.print (indent + key + " ");
            if (A != null) System.out.print ("A");
            if (B != null) System.out.print ("B");
            System.out.println ();
            if (children == null) return;
            indent += " ";
            for (Object o : children) ((NodeDiff) o).dump (indent);
        }
    }

    public class DeleteDiff extends Undoable
    {
        protected List<String> path;   // to immediate container
        protected int          index;  // where to insert among siblings
        protected NodeDiff     saved;  // The portion of the tree that was removed. TODO: this needs more thought.

        public DeleteDiff (NodeDiff node)
        {
            NodeDiff parent = (NodeDiff) node.getParent ();
            index           = parent.getIndex (node);
            // TODO: capture portion of tree that is being removed.
        }

        public void undo ()
        {
            super.undo ();
        }

        public void redo ()
        {
            super.redo ();
        }
    }
}
