/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.eq.search.NameEditor;
import gov.sandia.n2a.ui.eq.search.NodeBase;
import gov.sandia.n2a.ui.eq.search.NodeModel;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;


@SuppressWarnings("serial")
public class PanelSearch extends JPanel
{
    protected SearchThread           thread;
    protected JTextField             textQuery;
    protected NodeBase               root       = new NodeBase ();
    protected DefaultTreeModel       model      = new DefaultTreeModel (root);
    public    JTree                  tree       = new JTree (model);
    protected MNodeRenderer          renderer   = new MNodeRenderer ();
    public    NameEditor             nameEditor = new NameEditor (renderer);
    public    TransferHandler        transferHandler;
    public    List<String>           lastSelection;
    public    List<String>           insertAt;       // Path to node that next insertion should precede. If null, insert at top of uncategorized nodes.
    protected String                 lastQuery = ""; // for purpose of caching expanded nodes
    protected List<String[]>         expandedNodes = new ArrayList<String[]> ();

    public PanelSearch ()
    {
        tree.setRootVisible (false);
        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection.
        tree.setEditable (true);
        tree.setInvokesStopCellEditing (true);  // auto-save current edits, as much as possible
        tree.setDragEnabled (true);
        tree.setToggleClickCount (0);  // Disable expand/collapse on double-click
        ToolTipManager.sharedInstance ().registerComponent (tree);
        tree.setCellRenderer (renderer);
        tree.setCellEditor (nameEditor);
        tree.addTreeSelectionListener (nameEditor);
        //tree.putClientProperty ("JTree.lineStyle", "None");  // Get rid of lines that connect children to parents. Also need to hide handles, but that is more difficult (no option in JTree).

        InputMap inputMap = tree.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),     "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "delete");
        inputMap.put (KeyStroke.getKeyStroke ("SPACE"),      "select");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),      "select");
        inputMap.put (KeyStroke.getKeyStroke ("F2"),         "edit");

        ActionMap actionMap = tree.getActionMap ();
        Action selectPrevious = actionMap.get ("selectPrevious");
        Action selectParent   = actionMap.get ("selectParent");
        actionMap.put ("selectPrevious", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (tree.getLeadSelectionRow () == 0)
                {
                    textQuery.requestFocusInWindow ();
                    return;
                }
                selectPrevious.actionPerformed (e);
            }
        });
        actionMap.put ("selectParent", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                TreePath path = tree.getLeadSelectionPath ();
                if (path == null  ||  path.getPathCount () == 2  &&  tree.isCollapsed (path))  // This is a direct child of the root (which is not visible).
                {
                    textQuery.requestFocusInWindow ();
                }
                selectParent.actionPerformed (e);
            }
        });
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                PanelModel.instance.undoManager.add (new AddDoc ());
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                NodeModel n = getSelectedNodeModel ();
                if (n == null  ||  ! n.allowEdit ()) return;
                PanelModel.instance.undoManager.add (new DeleteDoc ((MDoc) AppData.models.child (n.key)));
            }
        });
        actionMap.put ("select", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                selectCurrent ();
            }
        });
        actionMap.put ("edit", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                NodeModel n = getSelectedNodeModel ();
                if (n == null  ||  ! n.allowEdit ()) return;
                tree.startEditingAtPath (new TreePath (n.getPath ()));
            }
        });

        tree.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                if (e.getClickCount () > 1) selectCurrent ();
            }
        });

        tree.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                restoreFocus ();
            }

            public void focusLost (FocusEvent e)
            {
                if (! e.isTemporary ()  &&  ! tree.isEditing ()) yieldFocus ();
            }
        });

        transferHandler = new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return xfer.isDataFlavorSupported (DataFlavor.stringFlavor)  ||  xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (! tree.isFocusOwner ()) yieldFocus ();

                Transferable xferable = xfer.getTransferable ();
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))
                {
                    try
                    {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) xferable.getTransferData (DataFlavor.javaFileListFlavor);
                        for (File file : files) PanelModel.importFile (file.toPath ());
                        return true;
                    }
                    catch (IOException | UnsupportedFlavorException e)
                    {
                    }
                }
                else if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))
                {
                    Schema schema;
                    MNode data = new MVolatile ();
                    TransferableNode xferNode = null;
                    try
                    {
                        StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                        schema = Schema.readAll (data, reader);
                        if (xferable.isDataFlavorSupported (TransferableNode.nodeFlavor)) xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
                    }
                    catch (IOException | UnsupportedFlavorException e)
                    {
                        return false;
                    }

                    if (! schema.type.contains ("Part")) return false;
                    PanelModel pm = PanelModel.instance;
                    if (xfer.isDrop ()  &&  xferNode != null  &&  xferNode.panel != pm.panelEquations) return false;  // Reject DnD from search or MRU
                    pm.undoManager.addEdit (new CompoundEdit ());
                    for (MNode n : data)  // data can contain several parts
                    {
                        AddDoc add = new AddDoc (n.key (), n);
                        if (xferNode != null  &&  xferNode.drag)
                        {
                            add.wasShowing = false;  // on the presumption that the sending side will create an Outsource operation, and thus wants to keep the old model in the equation tree
                            xferNode.newPartName = add.name;
                        }
                        pm.undoManager.add (add);
                        break;  // For now, we only support transferring a single part. To do more, we need to add collections in TransferableNode for both the node paths and the created part names.
                    }
                    if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) PanelModel.instance.undoManager.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.

                    return true;
                }

                return false;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY;
            }

            protected Transferable createTransferable (JComponent comp)
            {
                // TODO: consider creating a transferable that contains all the models in a selected category
                NodeModel n = getSelectedNodeModel ();
                if (n == null) return null;
                TransferableNode result = n.createTransferable ();
                result.panel = PanelSearch.this;
                return result;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                if (! tree.isFocusOwner ()) yieldFocus ();
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
            }
        };
        tree.setTransferHandler (transferHandler);


        textQuery = new JTextField ();

        inputMap = textQuery.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ESCAPE"), "cancel");
        inputMap.put (KeyStroke.getKeyStroke ("DOWN"),   "selectNext");
        inputMap.put (KeyStroke.getKeyStroke ("RIGHT"),  "selectNext");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),  "selectNext");

        actionMap = textQuery.getActionMap ();
        actionMap.put ("cancel", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                textQuery.setText ("");
            }
        });
        actionMap.put ("selectNext", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                lastSelection = null;
                tree.requestFocusInWindow ();
            }
        });

        textQuery.getDocument ().addDocumentListener (new DocumentListener ()
        {
            public void insertUpdate (DocumentEvent e)
            {
                search ();
            }

            public void removeUpdate (DocumentEvent e)
            {
                search ();
            }

            public void changedUpdate (DocumentEvent e)
            {
                search ();
            }
        });

        textQuery.setTransferHandler (new SafeTextTransferHandler ()
        {
            public boolean importData (TransferSupport support)
            {
                boolean result = super.importData (support);
                if (! result) result = transferHandler.importData (support);
                return result;
            }
        });


        Lay.BLtg (this,
            "N", Lay.BL ("C", textQuery, "eb=2"),
            "C", Lay.sp (tree)
        );

        search ();
    }

    public void search ()
    {
        if (thread != null) thread.stop = true;
        // Don't wait for thread to stop.

        String query = textQuery.getText ();
        thread = new SearchThread (query.trim ());
        thread.start ();
    }

    public NodeBase getSelectedNode ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return null;
        return (NodeBase) path.getLastPathComponent ();
    }

    public NodeModel getSelectedNodeModel ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return null;
        Object o = path.getLastPathComponent ();
        if (o instanceof NodeModel) return (NodeModel) o;
        return null;
    }

    public void selectCurrent ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;
        Object o = path.getLastPathComponent ();
        if (o instanceof NodeModel)
        {
            MNode doc = AppData.models.child (o.toString ());
            PanelModel.instance.panelMRU.useDoc (doc);
            recordSelected (doc);
        }
        else  // category
        {
            if (tree.isExpanded (path)) tree.collapsePath (path);
            else                        tree.expandPath (path);
        }
    }

    public static void recordSelected (final MNode doc)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                PanelEquations pe = PanelModel.instance.panelEquations;
                if (pe.record == doc) pe.takeFocus ();
                else                  pe.load (doc);
            }
        });
    }

    public void yieldFocus ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path != null) lastSelection = ((NodeBase) path.getLastPathComponent ()).getKeyPath ();
        // else leave lastSelection at its previous value
        tree.clearSelection ();
    }

    public void takeFocus ()
    {
        if (tree.isFocusOwner ()) restoreFocus ();
        else                      tree.requestFocusInWindow ();  // Triggers focus listener, which calls restoreFocus()
    }

    public void restoreFocus ()
    {
        if (tree.getSelectionCount () > 0) return;
        NodeBase n = nodeFor (lastSelection);
        TreePath path;
        if (n == null) path = tree.getPathForRow (0);
        else           path = new TreePath (n.getPath ());
        tree.setSelectionPath (path);
        tree.scrollPathToVisible (path);
    }

    public void forceSelection (List<String> selection)
    {
        lastSelection = selection;
        tree.clearSelection ();  // so that restoreFocus() will actually apply lastSelection
        takeFocus ();
    }

    public NodeBase nodeFor (List<String> path)
    {
        if (path == null) return null;
        String last = path.get (path.size () - 1);
        NodeBase n = root;
        for (String key : path)
        {
            NodeBase c = null;
            if (key.isEmpty ()) c = n.firstModel ();
            else                c = n.child (key, key == last);
            if (c == null) break;
            n = c;
        }
        if (n == root) return null;
        return n;
    }

    public List<String> currentPath ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return null;
        return ((NodeBase) path.getLastPathComponent ()).getKeyPath ();
    }

    /**
        Of all places that given key occurs in tree, returns the one most suitable given current selection.
    **/
    public NodeModel find (String key)
    {
        NodeBase n = null;
        List<String> currentSelection = currentPath ();
        if (currentSelection == null) currentSelection = lastSelection;
        if (currentSelection != null) n = nodeFor (currentSelection);
        if (! (n instanceof NodeModel)) n = null;
        if (n != null)  // Use current selection as starting context to search for key.
        {
            NodeBase p = (NodeBase) n.getParent ();  // could be root, or any category folder
            n = p.child (key, true);
        }
        if (n == null) n = root.findModel (key);  // key was not found, so do full search.
        return (NodeModel) n;
    }

    public List<String> pathAfter (String key)
    {
        NodeBase n = find (key);  // Determine best path to doc, if it is currently listed.
        if (n == null)  // Not found anywhere, so choose first uncategorized entry at top level.
        {
            List<String> result = new ArrayList<String> ();
            result.add ("");
            return result;
        }

        NodeBase next = (NodeBase) n.getNextSibling ();
        if (next != null) return next.getKeyPath ();

        // No next sibling, so choose first uncategorized entry at this level.
        List<String> result = n.getKeyPath ();
        result.set (result.size () - 1, "");
        return result;
    }

    public void removeDoc (String key)
    {
        if (lastSelection == null  ||  lastSelection.get (lastSelection.size () - 1).equals (key)) lastSelection = pathAfter (key);
        root.purge (key, model);
    }

    public void updateDoc (String oldKey, String newKey)
    {
        if (oldKey.equals (newKey)) return;
        root.replaceDoc (oldKey, newKey, model);
        if (lastSelection != null)
        {
            int last = lastSelection.size () - 1;
            if (lastSelection.get (last).equals (oldKey)) lastSelection.set (last, newKey);
        }
    }

    public void insertNextAt (List<String> at)
    {
        insertAt = at;
    }

    public void insertDoc (MNode doc)
    {
        String key = doc.key ();
        NodeBase n = find (key);
        if (n == null)
        {
            n = new NodeModel (key);

            NodeBase p;
            int index;
            NodeBase at = nodeFor (insertAt);
            if (at == null) at = root.firstModel ();
            if (at == null)
            {
                p = root;
                index = root.getChildCount ();  // past end of list, which will all be categories
            }
            else
            {
                p = (NodeBase) at.getParent ();
                index = p.getIndex (at);
            }

            model.insertNodeInto (n, p, index);
        }
        lastSelection = n.getKeyPath ();
        insertAt = null;
    }

    // Retrieve records matching the filter text, and deliver them to the model.
    public class SearchThread extends Thread
    {
        public String query;
        public boolean stop;

        public SearchThread (String query)
        {
            this.query = query.toLowerCase ();
        }

        @Override
        public void run ()
        {
            NodeBase newRoot = new NodeBase ();
            for (MNode i : AppData.models)
            {
                if (stop) return;
                String key = i.key ();
                if (key.toLowerCase ().contains (query))
                {
                    String[] categories = i.get ("$metadata", "gui", "category").split (",");
                    for (String category : categories)
                    {
                        category = category.trim ();
                        NodeModel n = new NodeModel (key);
                        if (category.isEmpty ()) newRoot.add (n);
                        else                     newRoot.insert (category, n);
                    }
                }
            }

            // Update of list should be atomic with respect to other ui events.
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    synchronized (model)
                    {
                        if (stop) return;

                        // Save list of open nodes
                        if (lastQuery.isEmpty ())
                        {
                            expandedNodes = new ArrayList<String[]> ();
                            Enumeration<TreePath> expandedPaths = tree.getExpandedDescendants (new TreePath (root.getPath ()));
                            if (expandedPaths != null)
                            {
                                while (expandedPaths.hasMoreElements ())
                                {
                                    TreePath path = expandedPaths.nextElement ();
                                    Object[] objectPath = path.getPath ();
                                    if (objectPath.length == 1) continue;  // Don't store root node.
                                    String[] stringPath = new String[objectPath.length - 1];
                                    for (int i = 1; i < objectPath.length; i++) stringPath[i-1] = ((NodeBase) objectPath[i]).toString ();
                                    expandedNodes.add (stringPath);
                                }
                            }
                        }
                        lastQuery = query;

                        // Switch to new tree
                        root = newRoot;
                        model.setRoot (newRoot);  // triggers repaint

                        // Restore open nodes
                        for (String[] stringPath : expandedNodes)
                        {
                            NodeBase c = root;
                            for (String key : stringPath)
                            {
                                c = c.child (key, false);
                                if (c == null) break;
                            }
                            if (c != null) tree.expandPath (new TreePath (c.getPath ()));
                        }

                        // Scroll to most recent selection. However, don't actually select it, since tree does not currently have focus.
                        NodeBase n = nodeFor (lastSelection);
                        if (n != null)
                        {
                            TreePath path = new TreePath (n.getPath ());
                            tree.expandPath (path);
                            tree.scrollPathToVisible (path);
                        }
                    }
                }
            });
        }
    }

    public static class MNodeRenderer extends DefaultTreeCellRenderer
    {
        protected Border border = BorderFactory.createEmptyBorder (0, 0, 1, 0);

        public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
        {
            super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

            NodeBase n = (NodeBase) value;
            setForeground (n.getColor (selected));
            setIcon (getIconFor (n, expanded, leaf));
            setBorder (border);  // Ensure a little space between icons. It would be nice to only use this when icon determines the overall size, but haven't figured out how to detect that yet.

            return this;
        }

        public Icon getIconFor (NodeBase node, boolean expanded, boolean leaf)
        {
            Icon result = node.getIcon (expanded);  // A node knows whether it should hold other nodes or not, so don't pass leaf to it.
            if (result != null) return result;
            if (leaf)     return getDefaultLeafIcon ();
            if (expanded) return getDefaultOpenIcon ();
            return               getDefaultClosedIcon ();
        }
    }
}
