/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.search.NameEditor;
import gov.sandia.n2a.ui.eq.search.NodeBase;
import gov.sandia.n2a.ui.eq.search.NodeCategory;
import gov.sandia.n2a.ui.eq.search.NodeModel;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.AddPart;
import gov.sandia.n2a.ui.eq.undo.ChangeCategory;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;
import gov.sandia.n2a.ui.settings.SettingsRepo;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
    protected SearchThread     threadSearch;
    protected JTextField       textQuery;
    protected NodeBase         root          = new NodeBase ();
    protected DefaultTreeModel model         = new DefaultTreeModel (root);
    public    JTree            tree;
    protected MNodeRenderer    renderer      = new MNodeRenderer ();
    public    NameEditor       nameEditor    = new NameEditor (renderer);
    public    TransferHandler  transferHandler;
    public    List<String>     lastSelection;
    public    List<String>     insertAt;           // Path to node that next insertion should precede. If null, insert at top of uncategorized nodes.
    protected String           lastQuery     = ""; // for purpose of caching expanded nodes
    protected List<String[]>   expandedNodes = new ArrayList<String[]> ();

    protected Map<String,Connector> connectors;
    protected ConnectThread         threadConnect;

    public PanelSearch ()
    {
        tree = new JTree (model)
        {
            public String getToolTipText (MouseEvent e)
            {
                TreePath path = getPathForLocation (e.getX (), e.getY ());
                if (path == null) return null;
                Object o = path.getLastPathComponent ();
                if (! (o instanceof NodeModel)) return null;
                NodeModel node = (NodeModel) o;

                MNode doc = AppData.models.child (node.key);
                if (doc == null) return null;
                MPart source = new MPart (doc);

                FontMetrics fm = getFontMetrics (getFont ());
                return gov.sandia.n2a.ui.eq.tree.NodeBase.getToolTipText (source, fm);
            }
        };
        tree.setRootVisible (false);
        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection.
        tree.setEditable (true);
        tree.setInvokesStopCellEditing (true);  // auto-save current edits, as much as possible
        tree.setDragEnabled (true);
        tree.setToggleClickCount (0);  // Disable expand/collapse on double-click
        tree.setRequestFocusEnabled (false);  // Don't request focus directly when clicked. Instead, let mouse listener do it.
        ToolTipManager.sharedInstance ().registerComponent (tree);
        tree.setCellRenderer (renderer);
        tree.setCellEditor (nameEditor);
        tree.addTreeSelectionListener (nameEditor);
        //tree.putClientProperty ("JTree.lineStyle", "None");  // Get rid of lines that connect children to parents. Also need to hide handles, but that is more difficult (no option in JTree).

        UndoManager um = MainFrame.instance.undoManager;

        InputMap inputMap = tree.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl shift EQUALS"), "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
        inputMap.put (KeyStroke.getKeyStroke ("SPACE"),             "select");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "select");
        inputMap.put (KeyStroke.getKeyStroke ("F2"),                "edit");

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
                um.apply (new AddDoc ());
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                NodeModel n = getSelectedNodeModel ();
                if (n == null  ||  ! n.allowEdit ()) return;
                um.apply (new DeleteDoc ((MDoc) AppData.models.child (n.key)));
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
            public void mouseClicked (MouseEvent me)
            {
                int clicks = me.getClickCount ();
                if (clicks == 1)
                {
                    int x = me.getX ();
                    int y = me.getY ();
                    TreePath path = tree.getClosestPathForLocation (x, y);
                    if (path != null)
                    {
                        lastSelection = ((NodeBase) path.getLastPathComponent ()).getKeyPath ();
                        tree.setSelectionPath (path);
                    }
                    takeFocus ();

                    if (path != null  &&  me.isControlDown ())  // Bring up context menu for moving between repos.
                    {
                        Object o = path.getLastPathComponent ();
                        if (o instanceof NodeModel)
                        {
                            JPopupMenu menuRepo = SettingsRepo.instance.createTransferMenu ("models/" + ((NodeModel) o).key);
                            menuRepo.show (tree, x, y);
                        }
                    }
                }
                else  // all click counts >1
                {
                    selectCurrent ();
                }
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
                if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))       return true;
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return true;
                return false;
            }

            public boolean importData (TransferSupport xfer)
            {
                if (! tree.isFocusOwner ()) yieldFocus ();

                Transferable xferable = xfer.getTransferable ();
                try
                {
                    if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))
                    {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) xferable.getTransferData (DataFlavor.javaFileListFlavor);
                        um.addEdit (new CompoundEdit ());  // in case there is more than one file
                        for (File file : files) PanelModel.importFile (file.toPath ());
                        um.endCompoundEdit ();
                        return true;
                    }
                    else if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))
                    {
                        MNode data = new MVolatile ();
                        StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                        Schema schema = Schema.readAll (data, reader);

                        TransferableNode xferNode = null;
                        if (xferable.isDataFlavorSupported (TransferableNode.nodeFlavor)) xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
    
                        if (! schema.type.contains ("Part")) return false;
                        PanelModel pm = PanelModel.instance;
                        if (xfer.isDrop ()  &&  xferNode != null  &&  xferNode.panel != pm.panelEquations)
                        {
                            if (xferNode.panel != pm.panelSearch) return false;  // Reject DnD from MRU
                            // This is a drag internal to this search panel, so treat it as a re-categorization.
    
                            // Determine old category.
                            if (xferNode.selection == null) return false;
                            NodeBase oldNode = nodeFor (xferNode.selection);
                            if (oldNode == null  ||  oldNode.getKeyPath ().size () < xferNode.selection.size ()) return false;
                            if (! (oldNode instanceof NodeModel)) return false;  // If oldeNode is a category, then the user is trying to move the entire category to a new place. For now, don't support this.
                            String key = ((NodeModel) oldNode).key;
                            MNode doc = AppData.models.child (key);
                            if (! AppData.models.isWriteable (doc)) return false;  // Must be able to change model in order to change category.
                            String oldCategory = oldNode.getCategory ();
    
                            // Determine new category.
                            TreePath path = ((JTree.DropLocation) xfer.getDropLocation ()).getPath ();
                            if (path == null) return false;
                            NodeBase newNode = (NodeBase) path.getLastPathComponent ();
                            String newCategory = newNode.getCategory ();
                            List<String> newSelection = newNode.getKeyPath ();
    
                            // Update metadata
                            if (newCategory.equals (oldCategory)) return true;  // Prevent damage to doc categories if DnD occurs when list is temporarily uncategorized, such as during connection search.
                            String current = getCategory (key);
                            List<String> currentList = new ArrayList<String> ();
                            for (String c : current.split (",")) currentList.add (c.trim ());
    
                            int index = currentList.indexOf (oldCategory);
                            if (index >= 0) currentList.set (index, newCategory);
                            else            currentList.add (newCategory);
    
                            String next = currentList.get (0);
                            for (int i = 1; i < currentList.size (); i++) next += "," + currentList.get (i);
    
                            if (! next.equals (current))
                            {
                                if (newNode instanceof NodeModel) newSelection.remove (newSelection.size () - 1);
                                newSelection.add (oldNode.toString ());
                                um.apply (new ChangeCategory (doc, next, xferNode.selection, newSelection));
                            }
                            return true;
                        }
    
                        um.addEdit (new CompoundEdit ());
                        for (MNode n : data)  // data can contain several parts
                        {
                            AddDoc add = new AddDoc (n.key (), n);
                            if (xferNode != null  &&  xferNode.drag)
                            {
                                add.wasShowing = false;  // on the presumption that the sending side will create an Outsource operation, and thus wants to keep the old model in the equation tree
                                xferNode.newPartName = add.name;
                            }
                            um.apply (add);
                            break;  // For now, we only support transferring a single part. To do more, we need to add collections in TransferableNode for both the node paths and the created part names.
                        }
                        if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) um.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.
    
                        return true;
                    }
                }
                catch (IOException | UnsupportedFlavorException e) {}

                return false;
            }

            public int getSourceActions (JComponent comp)
            {
                return LINK | COPY | MOVE;
            }

            int modifiers;
            public void exportAsDrag (JComponent comp, InputEvent e, int action)
            {
                modifiers = e.getModifiers ();
                super.exportAsDrag (comp, e, action);
            }

            protected Transferable createTransferable (JComponent comp)
            {
                // TODO: consider creating a transferable that contains all the models in a selected category
                NodeModel n = getSelectedNodeModel ();
                if (n == null) return null;
                TransferableNode result = n.createTransferable ();
                result.panel = PanelSearch.this;
                result.selection = n.getKeyPath ();
                result.modifiers = modifiers;
                modifiers = 0;
                return result;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                if (! tree.isFocusOwner ()) yieldFocus ();
                um.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
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
                // Always force a new search, even if textQuery is empty.
                // This lets us use the escape key to refresh the list after other types of search.
                if (textQuery.getText ().isEmpty ()) search ();
                else                                 textQuery.setText ("");
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
            public boolean canImport (TransferSupport xfer)
            {
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return true;
                return super.canImport (xfer);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return transferHandler.importData (xfer);
                if (super.importData (xfer)) return true;
                return transferHandler.importData (xfer);
            }
        });


        Lay.BLtg (this,
            "N", Lay.BL ("C", textQuery, "eb=2"),
            "C", Lay.sp (tree)
        );

        search ();
        new BuildConnectorIndex ().start ();
    }

    /**
        Retrieve and display models whose names contain the substring given in textQuery.
        Has the side effect of rebuilding categories, since they effectively all get
        filtered in parallel, and only those that contain a selected model will be displayed.
    **/
    public void search ()
    {
        if (threadSearch != null) threadSearch.stop = true;
        // Don't wait for thread to stop.

        String query = textQuery.getText ();
        if (query.startsWith ("(")) return;
        threadSearch = new SearchThread (query.trim ());
        threadSearch.start ();
    }

    public void search (List<NodePart> query)
    {
        if (threadConnect != null) threadConnect.stop = true;
        threadConnect = new ConnectThread (query);
        threadConnect.start ();
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
            if (key.isEmpty ())
            {
                c = n.firstModel ();
            }
            else
            {
                c = n.child (key, key == last);                            // Give preference to models as leaf node,
                if (c == null  &&  key == last) c = n.child (key, false);  // but still look for a category if no model exists.
            }
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
        synchronized (connectors) {connectors.remove (key);}
    }

    /**
        Four things can happen when a childChanged() message arrives:
        1) The underlying content of the record has changed;
        2) The key for the document has changed;
        3) An existing document has been exposed under the old key;
        4) An existing document has been hidden under the new key.
        What is the right response to all this?
        #1 -- Re-insert doc, since it may have entirely different categories now. Update connector index.
        #2 -- Replace the current key with the new one.
        #3 -- Re-insert the doc under the old key, since it may have entirely different categories. Update connector index.
        #4 -- Re-insert the doc under the new key, since it may have entirely different categories. Update connector index.
        It seems simplest just to do a fresh search while preserving focus. Also necessary to update connect index
        under both old and new keys.
    **/
    public void updateDoc (String oldKey, String newKey)
    {
        MNode oldDoc = AppData.models.child (oldKey);
        MNode newDoc = AppData.models.child (newKey);
        Connector oldConnector = null;
        if (oldDoc != null) oldConnector = new Connector (oldDoc);
        Connector newConnector = new Connector (newDoc);
        synchronized (connectors)
        {
            connectors.remove (oldKey);
            connectors.remove (newKey);
            if (oldConnector != null  &&  oldConnector.hasEndpoints ()) connectors.put (oldKey, oldConnector);
            if (                          newConnector.hasEndpoints ()) connectors.put (newKey, newConnector);
        }

        if (lastSelection != null)
        {
            int last = lastSelection.size () - 1;
            if (lastSelection.get (last).equals (oldKey)) lastSelection.set (last, newKey);
        }
        if (oldDoc == null  &&  ! AppData.models.isHiding (newKey))  // Simple name change (no hiding or unhiding)
        {
            root.replaceDoc (oldKey, newKey, model);
        }
        else  // General case: rebuild the entire tree.
        {
            search ();
        }
    }

    public void updateConnectors (MNode doc)
    {
        String key = doc.key ();
        Connector c = new Connector (doc);
        synchronized (connectors)
        {
            connectors.remove (key);
            if (c.hasEndpoints ()) connectors.put (key, c);
        }
    }

    public void insertNextAt (List<String> at)
    {
        insertAt = at;
    }

    public void insertDoc (MNode doc)
    {
        String key = doc.key ();
        Connector c = new Connector (doc);
        if (c.hasEndpoints ()) synchronized (connectors) {connectors.put (key, c);}

        NodeBase n = find (key);
        if (n == null)
        {
            n = new NodeModel (key);

            NodeBase p;
            int index = -1;
            NodeBase at = nodeFor (insertAt);
            if (at == null) at = root.firstModel ();
            if (at == null)
            {
                p = root;
            }
            else
            {
                if (at instanceof NodeCategory)
                {
                    if (at.getKeyPath ().size () < insertAt.size ())  // No models in target category (only sub-categories), so add new model at end.
                    {
                        p = at;
                    }
                    else  // insertAt refers directly to a category, so try to insert a peer.
                    {
                        // Should be after the last category under parent category.
                        p = (NodeBase) at.getParent ();
                        at = p.firstModel ();
                        if (at != null) index = p.getIndex (at);
                    }
                }
                else
                {
                    p = (NodeBase) at.getParent ();
                    index = p.getIndex (at);
                }
            }
            if (index < 0) index = p.getChildCount ();  // past end of list

            model.insertNodeInto (n, p, index);
        }
        lastSelection = n.getKeyPath ();
        insertAt = null;
    }

    /**
        Walks up the inheritance hierarchy (in proper order) until a gui.category tag is found.
        If none is found, return empty string.
    **/
    public String getCategory (String key)
    {
        MNode doc = AppData.models.child (key);
        if (doc == null) return "";
        String result = doc.get ("$metadata", "gui", "category");
        if (! result.isEmpty ()) return result;

        // No local definition, so check parents.
        for (String inherit : doc.get ("$inherit").split (","))
        {
            inherit = inherit.trim ().replace ("\"", "");
            result = getCategory (inherit);
            if (! result.isEmpty ()) return result;
        }
        return "";
    }

    public void saveExpandedNodes ()
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

    public void restoreExpandedNodes ()
    {
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
    }

    // Retrieve records matching the filter text, and deliver them to the model.
    public class SearchThread extends Thread
    {
        public String query;
        public boolean stop;

        public SearchThread (String query)
        {
            super ("Search Models");
            setDaemon (true);

            this.query = query.toLowerCase ();
        }

        public void run ()
        {
            NodeBase newRoot = new NodeBase ();
            for (MNode i : AppData.models)
            {
                if (stop) return;
                String key = i.key ();
                if (key.toLowerCase ().contains (query))
                {
                    for (String category : getCategory (key).split (",", -1))
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

                        // Switch to new tree
                        if (lastQuery.isEmpty ()) saveExpandedNodes ();
                        lastQuery = query;
                        root = newRoot;
                        model.setRoot (newRoot);  // triggers repaint
                        restoreExpandedNodes ();

                        // Scroll to most recent selection.
                        NodeBase n = nodeFor (lastSelection);
                        if (n != null)
                        {
                            TreePath path = new TreePath (n.getPath ());
                            tree.expandPath (path);
                            tree.scrollPathToVisible (path);
                            if (tree.isFocusOwner ()) tree.setSelectionPath (path);
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


    // -----------------------------------------------------------------------
    // The rest of this class is dedicated to connection matching.
    // Why is this here? Because it has to do with searching for parts.

    /**
        Utility to find the best matches for a given set of parts, independent of GUI.
        @param nodes A list MNodes that contain parts which inherit from some database part.
        Can be either sub-parts in a model or direct database parts themselves.
        @return A list of database keys for retrieving suitable connection parts.
        All returned keys will have the same (best) ranking. The caller can apply some
        other criteria to further down-select.
    **/
    public List<String> findConnectorFor (MNode... nodes)
    {
        List<EndpointTarget> targets = new ArrayList<EndpointTarget> ();
        for (MNode n : nodes) targets.add (new EndpointTarget (n));

        ConnectThread ct = new ConnectThread (null);  // We won't actually run this as a separate thread.
        TreeMap<Float,ArrayList<EndpointMatch>> matches = ct.score (targets);

        List<String> result = new ArrayList<String> ();
        if (! matches.isEmpty ())
        {
            Entry<Float,ArrayList<EndpointMatch>> e = matches.firstEntry ();
            for (EndpointMatch m : e.getValue ()) result.add (m.key);
        }
        return result;
    }

    /**
        Describes the set of parts that a connection endpoint can handle.
    **/
    public static class EndpointHandles
    {
        String      name;  // Of endpoint
        Set<String> partNames;

        /**
            Takes node for endpoint variable and extracts list of compatible parts.
        **/
        public EndpointHandles (MNode endpoint)
        {
            name = endpoint.key ();

            partNames = new HashSet<String> ();
            String line = endpoint.get ().split ("connect", 2)[1];
            line = line.replace ("(", "");
            line = line.replace (")", "");
            for (String p : line.split (","))
            {
                p = p.trim ().replace ("\"", "");
                partNames.add (p);
            }
            if (partNames.isEmpty ()) partNames = null;
        }

        public float score (EndpointTarget target)
        {
            if (partNames.isEmpty ()) return 0;
            float result = Float.POSITIVE_INFINITY;
            for (String p : partNames)
            {
                Integer s = target.ancestors.get (p);
                if (s == null) continue;
                result = Math.min (result, s);
            }
            return result;
        }

        public void dump ()
        {
            System.out.print ("  " + name + ": ");
            for (String pn : partNames) System.out.print (pn + ", ");
            System.out.println ();
        }
    }

    public static class EndpointTarget
    {
        NodePart            node;
        Map<String,Integer> ancestors = new HashMap<String,Integer> ();

        /**
            Takes a sub-part of model and interprets its $inherit line to form a set of ancestor parts.
            Ancestors are ranked by distance from the given child part. If an ancestor appears more than
            once, the closest occurrence determines the rank.
        **/
        public EndpointTarget (NodePart node)
        {
            this.node = node;
            process (node.source, 0);
        }

        public EndpointTarget (MNode part)
        {
            process (part, 0);
        }

        public void process (MNode part, int depth)
        {
            ancestors.put (part.key (), depth++);
            String[] inherits = part.get ("$inherit").split (",");
            for (String inherit : inherits)
            {
                inherit = inherit.trim ().replace ("\"", "");
                if (inherit.isEmpty ()) continue;
                Integer d = ancestors.get (inherit);
                if (d == null)
                {
                    MNode m = AppData.models.child (inherit);
                    if (m != null) process (m, depth);
                }
                else if (d > depth)
                {
                    ancestors.put (inherit, depth);
                }
            }
        }
    }

    /**
        One specific mapping from endpoint variables to target parts, along with score.
        Used to report result
    **/
    public static class EndpointMatch
    {
        String               key;  // of connection model
        Map<String,NodePart> matches = new HashMap<String,NodePart> ();  // from endpoint variable name to target part
        float                score;
    }

    public static class Connector
    {
        String                      key;  // of associated model
        Map<String,EndpointHandles> handles;

        /**
            Takes model and extracts endpoints.
        **/
        public Connector (MNode doc)
        {
            key = doc.key ();
            build ();
        }

        /**
            Rebuild list of endpoints.
        **/
        public void build ()
        {
            handles = new HashMap<String,EndpointHandles> ();
            MNode doc = AppData.models.child (key);
            MPart part = new MPart (doc);
            for (MNode c : part)
            {
                String value = c.get ();
                if (! Operator.containsConnect (value)) continue;
                EndpointHandles e = new EndpointHandles (c);
                handles.put (e.name, e);
            }
            if (handles.isEmpty ()) handles = null;  // release memory
        }

        public boolean hasEndpoints ()
        {
            return handles != null;
        }

        /**
            Rates how good a choice this connector is for the given set of target parts.
            Lower numbers are better. Result is only non-null if the connection is compatible.
            This requires that it have the right number of endpoints, and that each endpoint match
            at least one available class.
        **/
        public EndpointMatch score (List<EndpointTarget> targets)
        {
            int count = targets.size ();
            if (count != handles.size ()) return null;

            // Build a matrix of all possible endpoint assignments and their scores.
            // First dimension is handles, and follows its natural enumeration order, which we assume is constant.
            // Second dimension is targets, and follows simple index order.
            WorkingState state = new WorkingState ();
            state.scores = new float[count][count];
            int[] indices = new int[count];
            int i = 0;
            for (String A : handles.keySet ())
            {
                EndpointHandles H = handles.get (A);
                for (int j = 0; j < count; j++)
                {
                    state.scores[i][j] = H.score (targets.get (j));
                }
                indices[i] = i++;
            }

            // Test all permutations
            state.bestPermutation = new int[count];
            state.bestScore = Float.POSITIVE_INFINITY;
            generate (count, indices, state);

            EndpointMatch result = new EndpointMatch ();
            i = 0;
            for (String A : handles.keySet ())
            {
                EndpointTarget T = targets.get (state.bestPermutation[i++]);
                result.matches.put (A, T.node);
            }
            result.score = state.bestScore;

            return result;
        }

        /**
            Use Heap's Algorithm to generate all permutations of targets.
            https://en.wikipedia.org/wiki/Heap%27s_algorithm
            For any given query, the original order is the first thing tested.
            This means that the user-specified direction of the connection will take
            precedence over any other, provided they have the same score.
        **/
        public void generate (int k, int[] indices, WorkingState state)
        {
            if (k == 1)  // Do the actual test on the current permutation.
            {
                float score = 0;
                for (int i = 0; i < indices.length; i++)
                {
                    score += state.scores[i][indices[i]];
                }
                if (score < state.bestScore)
                {
                    state.bestScore = score;
                    for (int i = 0; i < indices.length; i++) state.bestPermutation[i] = indices[i];
                }
            }
            else  // Generate sub-permutations.
            {
                generate (k-1, indices, state);
                for (int i = 0; i < k-1; i++)
                {
                    if (k % 2 == 0) swap (indices, i, k-1);
                    else            swap (indices, 0, k-1);
                    generate (k-1, indices, state);
                }
            }
        }

        public void swap (int[] A, int i, int j)
        {
            int temp = A[i];
            A[i] = A[j];
            A[j] = temp;
        }

        public void dump ()
        {
            System.out.println (key);
            for (EndpointHandles h : handles.values ()) h.dump ();
        }

        public static class WorkingState
        {
            float[][] scores;
            int[]     bestPermutation;
            float     bestScore;
        }
    }

    // Initialize connector index.
    // TODO: also need incremental maintenance of connector index
    public class BuildConnectorIndex extends Thread
    {
        public BuildConnectorIndex ()
        {
            super ("Build Connector Index");
            setDaemon (true);
        }

        public void run ()
        {
            connectors = new HashMap<String,Connector> ();
            for (MNode i : AppData.models)
            {
                Connector c = new Connector (i);
                if (c.hasEndpoints ()) synchronized (connectors) {connectors.put (c.key, c);}
            }
            //for (Connector c : connectors.values ()) c.dump ();  // debug dump of index
        }
    }

    // Find best candidate for a connection between two parts.
    public class ConnectThread extends Thread
    {
        public boolean        stop;
        public List<NodePart> query;
        public NodePart       part;

        /**
            This constructor is run on the EDT, so it can safely collect current UI state.
        **/
        public ConnectThread (List<NodePart> nodes)
        {
            super ("Search Connections");
            setDaemon (true);

            query = nodes;
            part = PanelModel.instance.panelEquations.part;
        }

        @Override
        public void run ()
        {
            // Convert query into set of endpoint targets
            List<EndpointTarget> targets = new ArrayList<EndpointTarget> ();
            for (NodePart n : query) targets.add (new EndpointTarget (n));

            // Score each candidate
            TreeMap<Float,ArrayList<EndpointMatch>> matches = score (targets);

            // Rebuild search list.
            NodeBase newRoot = new NodeBase ();
            for (ArrayList<EndpointMatch> alem : matches.values ())
            {
                if (stop) return;
                for (EndpointMatch em : alem)
                {
                    newRoot.add (new NodeModel (em.key));
                }
            }

            // Create a new connection.
            MNode data = new MVolatile ();
            if (matches.isEmpty ())  // No connection part is applicable.
            {
                char k = 'A';
                for (NodePart p : query)
                {
                    data.set (p.source.key (), "" + k);
                    k++;
                }
            }
            else  // Pick the best candidate.
            {
                EndpointMatch m = matches.firstEntry ().getValue ().get (0);
                data.set (m.key, "$inherit");
                for (String key : m.matches.keySet ())
                {
                    NodePart p = m.matches.get (key);
                    data.set (p.source.key (), key);  // Assign name of target part to endpoint variable.
                }
            }

            // Determine position in graph.
            float x     = 0;
            float y     = 0;
            int   count = 0;
            for (NodePart p : query)
            {
                if (p.graph != null)
                {
                    Point l = p.graph.getLocation ();
                    x += l.x;
                    y += l.y;
                    count++;
                }
            }
            Point center = null;
            if (count > 1)
            {
                GraphPanel graphPanel = PanelModel.instance.panelEquations.panelEquationGraph.graphPanel;
                center = new Point ();
                center.x = Math.round (x / count) - graphPanel.offset.x;
                center.y = Math.round (y / count) - graphPanel.offset.y;
            }

            // UI changes must be done on the EDT.
            NodePart parent = query.get (0).getTrueParent ();
            final Point c = center;
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    if (stop  ||  part != PanelModel.instance.panelEquations.part) return;

                    if (newRoot.getChildCount () > 1)
                    {
                        // Update query field
                        if (lastQuery.isEmpty ()) saveExpandedNodes ();
                        lastQuery = "(connection; ESC to clear)";
                        textQuery.setText (lastQuery);

                        // Switch to new tree
                        root = newRoot;
                        model.setRoot (newRoot);  // triggers repaint
                    }

                    AddPart ap = new AddPart (parent, parent.getChildCount (), data, c);
                    MainFrame.instance.undoManager.apply (ap);
                }
            });
        }

        public TreeMap<Float,ArrayList<EndpointMatch>> score (List<EndpointTarget> targets)
        {
            TreeMap<Float,ArrayList<EndpointMatch>> result = new TreeMap<Float,ArrayList<EndpointMatch>> ();
            for (Connector c : connectors.values ())
            {
                if (stop) break;
                EndpointMatch m = c.score (targets);
                if (m == null  ||  Float.isInfinite (m.score)) continue;
                m.key = c.key;
                ArrayList<EndpointMatch> tranch = result.get (m.score);
                if (tranch == null)
                {
                    tranch = new ArrayList<EndpointMatch> ();
                    result.put (m.score, tranch);
                }
                tranch.add (m);
            }
            return result;
        }
    }
}
