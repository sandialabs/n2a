/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.MainFrame;
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
import javax.swing.InputMap;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.undo.CannotUndoException;


@SuppressWarnings("serial")
public class PanelDiff extends JTree
{
    protected SettingsRepo     container;
    protected NodeDiff         root        = new NodeDiff ();
    protected DefaultTreeModel model       = new DefaultTreeModel (root);
    protected DiffRenderer     renderer    = new DiffRenderer ();
    protected Delta            lastDelta;
    protected List<String>     lastSelection;

    public PanelDiff (SettingsRepo container)
    {
        this.container = container;

        setModel (model);
        setRootVisible (false);
        setShowsRootHandles (true);
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
                if (lastDelta.deleted)  // Force document to be restored in full, rather than allowing targeted undelete.
                {
                    MainFrame.instance.undoManager.add (container.new RevertDelta (lastDelta));
                    return;
                }

                NodeDiff n = (NodeDiff) getLastSelectedPathComponent ();
                if (n == null  ||  n == root) return;

                // Find nearest ancestor with an actual difference other than this node.
                NodeDiff p = (NodeDiff) n.getParent ();
                while (true)
                {
                    if (p.getChildCount () > 1  ||  p.A == null  ||  p.B == null  ||  p.A.data () != p.B.data ()  ||  ! p.A.get().equals (p.B.get ())) break;
                    NodeDiff nextP = (NodeDiff) p.getParent ();
                    if (nextP == null) break;
                    n = p;
                    p = nextP;
                }

                // Guard against deleting entire document. Such changes must be relegated to RevertDelta.
                if (lastDelta.untracked  &&  p == root)
                {
                    MainFrame.instance.undoManager.add (container.new RevertDelta (lastDelta));
                    return;
                }

                if (n != null) MainFrame.instance.undoManager.add (new RevertDiff (n));
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

    public void load (Delta delta)
    {
        if (lastDelta != null  &&  delta.equals (lastDelta)) return;
        lastDelta     = delta;
        lastSelection = null;
        MNode A = delta.getOriginal ();
        MNode B = delta.getDocument ();
        root = new NodeDiff ();
        root.key = delta.name;
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
            if (A == null) return;
            if (key.isEmpty ()) key = A.key ();
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
            if (B == null) return;
            if (key.isEmpty ()) key = B.key ();
            if (A == null)
            {
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
                    if (n.getChildCount () == 0  &&  n.A != null  &&  n.A.data () == n.B.data ()  &&  n.A.get ().equals (n.B.get ()))
                    {
                        remove (n);
                    }
                }
            }
        }

        public void expandAll ()
        {
            expandPath (new TreePath (getPath ()));
            if (children == null) return;
            for (Object o : children) ((NodeDiff) o).expandAll ();
        }

        public Color getColor (boolean selected)
        {
            if (selected)
            {
                if (A == null) return renderer.colorSelectedB;
                if (B == null) return renderer.colorSelectedA;
                if (A.data () == B.data ()  &&  A.get ().equals (B.get ())) return renderer.colorSelectedSame;
                return renderer.colorSelectedDiff;
            }
            else
            {
                if (A == null) return renderer.colorB;
                if (B == null) return renderer.colorA;
                if (A.data () == B.data ()  &&  A.get ().equals (B.get ())) return renderer.colorSame;
                return renderer.colorDiff;
            }
        }

        public List<String> getKeyPath ()
        {
            List<String> result;
            NodeDiff parent = (NodeDiff) getParent ();
            if (parent == null) result = new ArrayList<String> ();
            else                result = parent.getKeyPath ();
            result.add (key);
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
                String result = key;
                if (B != null)
                {
                    if (A == null)
                    {
                        if (B.data ()) result += " = " + B.get ();
                    }
                    else  // Both A and B are present, so compare them.
                    {
                        if (A.data () == B.data ()  &&  A.get ().equals (B.get ()))  // same content
                        {
                            if (B.data ()) result += " = " + B.get ();
                        }
                        else  // different
                        {
                            result  = "<html>";
                            result += "<s>" + escape (key) + " = " + escape (A.get ()) + "</s><br/>";
                            result +=         escape (key) + " = " + escape (B.get ());
                            result += "</html>";
                        }
                    }
                }
                else if (A != null)
                {
                    if (A.data ()) result += " = " + A.get ();
                }
                userObject = result;
            }
            return userObject.toString ();
        }

        public String escape (String value)
        {
            value = value.replace ("&",  "&amp;");
            value = value.replace ("<",  "&lt;");
            value = value.replace (">",  "&gt;");
            value = value.replace ("\"", "&quot;");
            return value;
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

    public class RevertDiff extends Undoable
    {
        protected GitWrapper   git;
        protected List<String> path;   // To immediate container. Starts with name of document itself, so this can handle entire document differences.
        protected String       name;   // Of node to be restored.
        protected int          index;  // where to insert among siblings
        protected MNode        B;      // Snapshot of document tree. Could be null. Key is ignored in favor "name" member variable above.

        public RevertDiff (NodeDiff node)
        {
            git   = lastDelta.git;
            NodeDiff p = (NodeDiff) node.getParent ();  // Assume that "node" is never root.
            path  = p.getKeyPath ();
            name  = node.key;
            index = p.getIndex (node);
            if (node.B != null)
            {
                B = new MVolatile ();
                B.merge (node.B);
            }
        }

        public NodeDiff restore (String[] keyArray)
        {
            // Select repository
            int row = container.repoModel.gitRepos.indexOf (git);
            if (row < 0) throw new CannotUndoException ();
            if (container.repoTable.getSelectedRow () != row) container.repoTable.changeSelection (row, 3, false, false);

            // Select document
            // If revert results in complete removal of the Delta, then we should convert it to a RevertDelta instead.
            // Thus, we should be able to assume a Delta always exists when executing a RevertDiff.
            Delta delta = container.gitModel.deltaFor (path.get (0));
            if (delta == null) throw new CannotUndoException ();
            load (delta);

            // Locate parent of changed node
            NodeDiff p = root;
            int i = 0;
            for (String key : path.subList (1, path.size ()))
            {
                p = p.child (key);
                if (p == null) throw new CannotUndoException ();
                keyArray[i++] = key;
            }
            keyArray[i] = name;
            return p;
        }

        public void undo ()
        {
            super.undo ();
            String[] keyArray = new String[path.size ()];
            NodeDiff p = restore (keyArray);

            // Change document
            // Document should always exist. If revert results in the complete erasure of the document, then it should be a RevertDelta instead.
            // Likewise, if a revert causes the document to come into existence, then we force the change to affect the document as a whole.
            // Thus, no need to delete document here as part of undo.
            MDoc doc = (MDoc) root.B;
            if (B == null) doc.clear (keyArray);  // Could leave behind empty parent nodes that didn't originally exist. This is probably a rare case, so don't worry about it for now.
            else           doc.set (B, keyArray);
            doc.save ();
            lastDelta.notifyDir ();

            // Rebuild subtree
            // We assume that p has no child with key==name.
            NodeDiff c = new NodeDiff ();
            p.add (c);
            if (root.A != null) c.buildA (root.A.child (keyArray));
            c.buildB (doc.child (keyArray));

            // Update display
            model.nodeStructureChanged (p);
            p.expandAll ();
            TreePath tp = new TreePath (c.getPath ());
            setSelectionPath (tp);
            scrollPathToVisible (tp);
        }

        public void redo ()
        {
            super.redo ();
            String[] keyArray = new String[path.size ()];
            NodeDiff p = restore (keyArray);
            NodeDiff c = p.child (name);  // must exist

            // Change document B to match original A.
            MDoc doc = (MDoc) root.B;
            if (c.A == null) doc.clear (keyArray);
            else             doc.set (c.A, keyArray);  // Automatically sets any missing parent keys in doc. They will be undefined nodes.
            doc.save ();
            lastDelta.notifyDir ();

            // Erase NodeDiff subtree, including named node.
            p.remove (c);
            model.nodeStructureChanged (p);
            p.expandAll ();
            TreePath tp = new TreePath (p.getPath ());
            setSelectionPath (tp);
            scrollPathToVisible (tp);
        }
    }
}
