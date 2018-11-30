/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.Move;
import gov.sandia.n2a.ui.eq.undo.Outsource;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.PanelRun;

import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

@SuppressWarnings("serial")
public class PanelEquationTree extends JPanel
{
    protected int jobCount = 0;  // for launching jobs

    // Tree
    public    JTree                 tree;
    public    FilteredTreeModel     model;
    public    NodePart              root;
    public    MNode                 record;
    public    boolean               locked;
    protected JScrollPane           scrollPane;
    protected Map<MNode,StoredPath> focusCache = new HashMap<MNode,StoredPath> ();
    protected boolean               needsFullRepaint;

    // Controls
    protected JButton buttonAddModel;
    protected JButton buttonAddPart;
    protected JButton buttonAddVariable;
    protected JButton buttonAddEquation;
    protected JButton buttonAddAnnotation;
    protected JButton buttonAddReference;
    protected JButton buttonFilter;
    protected JButton buttonRun;
    protected JButton buttonExport;
    protected JButton buttonImport;

    protected JPopupMenu menuPopup;
    protected JPopupMenu menuFilter;
    protected long       menuFilterCanceledAt = 0;

    protected static ImageIcon iconFilterRevoked = ImageUtil.getImage ("filterRevoked.png");
    protected static ImageIcon iconFilterAll     = ImageUtil.getImage ("filter.png");
    protected static ImageIcon iconFilterParam   = ImageUtil.getImage ("filterParam.png");
    protected static ImageIcon iconFilterLocal   = ImageUtil.getImage ("filterLocal.png");
    protected static ImageIcon iconLocked        = ImageUtil.getImage ("locked.png");
    protected static ImageIcon iconUnlocked      = ImageUtil.getImage ("unlocked.png");

    /**
        A data flavor that lets PanelSearch extract a TransferableNode instance for the purpose of adding info to it for our local exportDone().
        This is necessary because Swing packs the Transferable into a proxy object which is sewn shut.
    **/
    public static final DataFlavor nodeFlavor = new DataFlavor (TransferableNode.class, null);

    @SuppressWarnings("deprecation")
    public class TransferableNode implements Transferable, ClipboardOwner
    {
        public String       data;
        public List<String> path;
        public boolean      drag;
        public String       newPartName;  // If set non-null by the receiver (nasty hack), then this transfer resulted the creation of a new part.

        public TransferableNode (String data, NodeBase source, boolean drag)
        {
            this.data = data;
            path      = source.getKeyPath ();
            this.drag = drag;
        }

        public NodeBase getSource ()
        {
            MNode doc = AppData.models.child (path.get (0));
            if (doc != record) return null;

            NodeBase result = root;
            for (int i = 1; i < path.size (); i++)
            {
                result = (NodeBase) result.child (path.get (i));
                if (result == null) break;
            }
            return result;
        }

        @Override
        public void lostOwnership (Clipboard clipboard, Transferable contents)
        {
        }

        @Override
        public DataFlavor[] getTransferDataFlavors ()
        {
            DataFlavor[] result = new DataFlavor[3];
            result[0] = DataFlavor.stringFlavor;
            result[1] = DataFlavor.plainTextFlavor;
            result[2] = nodeFlavor;
            return result;
        }

        @Override
        public boolean isDataFlavorSupported (DataFlavor flavor)
        {
            if (flavor.equals (DataFlavor.stringFlavor   )) return true;
            if (flavor.equals (DataFlavor.plainTextFlavor)) return true;
            if (flavor.equals (nodeFlavor                )) return true;
            return false;
        }

        @Override
        public Object getTransferData (DataFlavor flavor) throws UnsupportedFlavorException, IOException
        {
            if (flavor.equals (DataFlavor.stringFlavor   )) return data;
            if (flavor.equals (DataFlavor.plainTextFlavor)) return new StringReader (data);
            if (flavor.equals (nodeFlavor                )) return this;
            throw new UnsupportedFlavorException (flavor);
        }
    }

    // The main constructor. Most of the real work of setting up the UI is here, including some fairly elaborate listeners.
    public PanelEquationTree ()
    {
        model = new FilteredTreeModel (null);
        tree  = new JTree (model)
        {
            @Override
            public String convertValueToText (Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                if (value == null) return "";
                return ((NodeBase) value).getText (expanded, false);
            }
        };

        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection. It only makes deletes and moves more complicated.
        tree.setEditable (true);
        tree.setInvokesStopCellEditing (true);  // auto-save current edits, as much as possible
        tree.setDragEnabled (true);
        tree.setToggleClickCount (0);  // Disable expand/collapse on double-click

        EquationTreeCellRenderer renderer = new EquationTreeCellRenderer ();
        tree.setCellRenderer (renderer);

        final EquationTreeCellEditor editor = new EquationTreeCellEditor (tree, renderer);
        editor.addCellEditorListener (new CellEditorListener ()
        {
            @Override
            public void editingStopped (ChangeEvent e)
            {
                NodeBase node = editor.editingNode;
                editor.editingNode = null;
                node.applyEdit (tree);
            }

            @Override
            public void editingCanceled (ChangeEvent e)
            {
                NodeBase node = editor.editingNode;
                editor.editingNode = null;

                // We only get back an empty string if we explicitly set it before editing starts.
                // Certain types of nodes do this when inserting a new instance into the tree, via NodeBase.add()
                // We desire in this case that escape cause the new node to evaporate.
                Object o = node.getUserObject ();
                if (! (o instanceof String)) return;

                NodeBase parent = (NodeBase) node.getParent ();
                if (((String) o).isEmpty ())
                {
                    node.delete (tree, true);
                }
                else  // The text has been restored to the original value set in node's user object just before edit. However, that has column alignment removed, so re-establish it.
                {
                    if (parent != null)
                    {
                        parent.updateTabStops (node.getFontMetrics (tree));
                        parent.allNodesChanged (model);
                    }
                }
            }
        });
        tree.setCellEditor (editor);

        InputMap inputMap = tree.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("shift UP"),   "moveUp");
        inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"), "moveDown");
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),     "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),      "startEditing"); 
        inputMap.put (KeyStroke.getKeyStroke ("ctrl ENTER"), "startEditing");

        ActionMap actionMap = tree.getActionMap ();
        actionMap.put ("moveUp", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                moveSelected (-1);
            }
        });
        actionMap.put ("moveDown", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                moveSelected (1);
            }
        });
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("");
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                deleteSelected ();
            }
        });
        actionMap.put ("startEditing", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                TreePath path = tree.getSelectionPath ();
                if (path != null  &&  ! locked)
                {
                    boolean isControlDown = (e.getModifiers () & ActionEvent.CTRL_MASK) != 0;
                    if (isControlDown  &&  ! (path.getLastPathComponent () instanceof NodePart)) editor.multiLineRequested = true;
                    tree.startEditingAtPath (path);
                }
            }
        });

        tree.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                if (! locked  &&  SwingUtilities.isLeftMouseButton (e)  &&  e.getClickCount () == 2)
                {
                    int x = e.getX ();
                    int y = e.getY ();
                    TreePath path = tree.getClosestPathForLocation (x, y);
                    if (path != null)
                    {
                        Rectangle r = tree.getPathBounds (path);
                        if (r.contains (x, y))
                        {
                            tree.setSelectionPath (path);
                            tree.startEditingAtPath (path);
                        }
                    }
                }
                else if (SwingUtilities.isRightMouseButton (e)  &&   e.getClickCount () == 1)
                {
                    TreePath path = tree.getPathForLocation (e.getX (), e.getY ());
                    if (path != null)
                    {
                        tree.setSelectionPath (path);
                        menuPopup.show (tree, e.getX (), e.getY ());
                    }
                }
            }
        });

        // Hack for slow Swing repaint when clicking to select new node
        tree.addTreeSelectionListener (new TreeSelectionListener ()
        {
            NodeBase oldSelection;
            Rectangle oldBounds;

            public void valueChanged (TreeSelectionEvent e)
            {
                if (! e.isAddedPath ()) return;
                TreePath path = e.getPath ();
                NodeBase newSelection = (NodeBase) path.getLastPathComponent ();
                if (newSelection == oldSelection) return;

                if (oldBounds != null) tree.paintImmediately (oldBounds);
                Rectangle newBounds = tree.getPathBounds (path);
                if (newBounds != null) tree.paintImmediately (newBounds);
                oldSelection = newSelection;
                oldBounds    = newBounds;
            }
        });

        tree.addTreeWillExpandListener (new TreeWillExpandListener ()
        {
            @Override
            public void treeWillExpand (TreeExpansionEvent event) throws ExpandVetoException
            {
            }

            @Override
            public void treeWillCollapse (TreeExpansionEvent event) throws ExpandVetoException
            {
                TreePath path = event.getPath ();
                if (((NodeBase) path.getLastPathComponent ()).isRoot ()) throw new ExpandVetoException (event);
            }
        });

        tree.addTreeExpansionListener (new TreeExpansionListener ()
        {
            public void treeExpanded (TreeExpansionEvent event)
            {
                repaintSouth (event.getPath ());
            }

            public void treeCollapsed (TreeExpansionEvent event)
            {
                repaintSouth (event.getPath ());
            }
        });

        tree.setTransferHandler (new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return ! locked  &&  xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (locked) return false;

                MNode data = new MVolatile ();
                Schema schema = new Schema ();
                TransferableNode xferNode = null;  // used only to detect if the source is ourselves (equation tree)
                try
                {
                    Transferable xferable = xfer.getTransferable ();
                    StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                    schema.readAll (reader, data);
                    if (xferable.isDataFlavorSupported (PanelEquationTree.nodeFlavor)) xferNode = (TransferableNode) xferable.getTransferData (PanelEquationTree.nodeFlavor);
                }
                catch (IOException | UnsupportedFlavorException e)
                {
                    return false;
                }

                PanelModel mep = PanelModel.instance;
                mep.undoManager.addEdit (new CompoundEdit ());

                // Determine paste/drop target
                TreePath path;
                if (xfer.isDrop ()) path = ((JTree.DropLocation) xfer.getDropLocation ()).getPath ();
                else                path = tree.getSelectionPath ();
                if (path == null)
                {
                    if (root == null) PanelModel.instance.undoManager.add (new AddDoc ());
                    tree.setSelectionRow (0);
                    path = tree.getSelectionPath ();
                }
                if (xfer.isDrop ()) tree.setSelectionPath (path);
                NodeBase target = (NodeBase) path.getLastPathComponent ();

                // An import can either be a new node in the tree, or a link (via inheritance) to an existing part.
                // In the case of a link, the part may need to be fully imported if it does not already exist in the db.
                boolean result = false;
                if (schema.type.startsWith ("Clip"))
                {
                    result = true;
                    for (MNode child : data)
                    {
                        NodeBase added = target.add (schema.type.substring (4), tree, child);
                        if (added == null)
                        {
                            result = false;
                            break;
                        }
                    }
                }
                else if (schema.type.equals ("Part"))
                {
                    result = true;
                    for (MNode child : data)  // There could be multiple parts.
                    {
                        // Ensure the part is in our db
                        String key = child.key ();
                        if (AppData.models.child (key) == null) mep.undoManager.add (new AddDoc (key, child));

                        // Create an include-style part
                        MNode include = new MVolatile ();  // Note the empty key. This enables AddPart to generate a name.
                        include.merge (child);  // TODO: What if this brings in a $inherit line, and that line does not match the $inherit line in the source part? One possibility is to add the new values to the end of the $inherit line created below.
                        include.clear ("$inherit");  // get rid of IDs from included part, so they won't override the new $inherit line ...
                        include.set ("$inherit", "\"" + key + "\"");
                        NodeBase added = target.add ("Part", tree, include);
                        if (added == null)
                        {
                            result = false;
                            break;
                        }
                    }
                }
                if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) mep.undoManager.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.
                return result;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY_OR_MOVE;
            }

            boolean dragInitiated;  // This is a horrible hack, but the simplest way to override the default MOVE action chosen internally by Swing.
            public void exportAsDrag (JComponent comp, InputEvent e, int action)
            {
                dragInitiated = true;
                super.exportAsDrag (comp, e, action);
            }

            protected Transferable createTransferable (JComponent comp)
            {
                boolean drag = dragInitiated;
                dragInitiated = false;

                NodeBase node = getSelected ();
                if (node == null) return null;
                MVolatile copy = new MVolatile ();
                node.copy (copy);
                if (node == root) copy.set (node.source.key (), "");  // Remove file information from root node, if that is what we are sending.

                Schema schema = new Schema (1, "Clip" + node.getTypeName ());
                StringWriter writer = new StringWriter ();
                try
                {
                    schema.write (writer);
                    for (MNode c : copy) c.write (writer);
                    writer.close ();

                    return new TransferableNode (writer.toString (), node, drag);
                }
                catch (IOException e)
                {
                }

                return null;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                TransferableNode tn = (TransferableNode) data;
                if (action == MOVE  &&  ! locked)
                {
                    // It is possible for the node to be removed from the tree before we get to it.
                    // For example, a local drop of an $inherit node will cause the tree to rebuild.
                    NodeBase node = ((TransferableNode) data).getSource ();
                    if (node != null)
                    {
                        if (tn.drag)
                        {
                            if (tn.newPartName != null  &&  node != root  &&  node.source.isFromTopDocument ())
                            {
                                // Change this node into an include of the newly-created part.
                                PanelModel.instance.undoManager.add (new Outsource ((NodePart) node, tn.newPartName));
                            }
                        }
                        else
                        {
                            node.delete (tree, false);
                        }
                    }
                }
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
            }
        });

        tree.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                if (tree.getSelectionCount () < 1)
                {
                    StoredPath sp = focusCache.get (record);
                    if (sp == null) tree.setSelectionRow (0);
                    else            sp.restore (tree);
                }
            }

            public void focusLost (FocusEvent e)
            {
                // The shift to the editing component appears as a loss of focus.
                // The shift to a popup menu appears as a "temporary" loss of focus.
                if (! e.isTemporary ()  &&  ! tree.isEditing ()) yieldFocus ();
            }
        });

        buttonAddModel = new JButton (ImageUtil.getImage ("explore.gif"));
        buttonAddModel.setMargin (new Insets (2, 2, 2, 2));
        buttonAddModel.setFocusable (false);
        buttonAddModel.setToolTipText ("New Model");
        buttonAddModel.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                tree.stopEditing ();
                PanelModel.instance.undoManager.add (new AddDoc ());
            }
        });

        buttonAddPart = new JButton (ImageUtil.getImage ("comp.gif"));
        buttonAddPart.setMargin (new Insets (2, 2, 2, 2));
        buttonAddPart.setFocusable (false);
        buttonAddPart.setToolTipText ("Add Part");
        buttonAddPart.setActionCommand ("Part");
        buttonAddPart.addActionListener (listenerAdd);

        buttonAddVariable = new JButton (ImageUtil.getImage ("delta.png"));
        buttonAddVariable.setMargin (new Insets (2, 2, 2, 2));
        buttonAddVariable.setFocusable (false);
        buttonAddVariable.setToolTipText ("Add Variable");
        buttonAddVariable.setActionCommand ("Variable");
        buttonAddVariable.addActionListener (listenerAdd);

        buttonAddEquation = new JButton (ImageUtil.getImage ("assign.png"));
        buttonAddEquation.setMargin (new Insets (2, 2, 2, 2));
        buttonAddEquation.setFocusable (false);
        buttonAddEquation.setToolTipText ("Add Equation");
        buttonAddEquation.setActionCommand ("Equation");
        buttonAddEquation.addActionListener (listenerAdd);

        buttonAddAnnotation = new JButton (ImageUtil.getImage ("edit.gif"));
        buttonAddAnnotation.setMargin (new Insets (2, 2, 2, 2));
        buttonAddAnnotation.setFocusable (false);
        buttonAddAnnotation.setToolTipText ("Add Annotation");
        buttonAddAnnotation.setActionCommand ("Annotation");
        buttonAddAnnotation.addActionListener (listenerAdd);

        buttonAddReference = new JButton (ImageUtil.getImage ("book.gif"));
        buttonAddReference.setMargin (new Insets (2, 2, 2, 2));
        buttonAddReference.setFocusable (false);
        buttonAddReference.setToolTipText ("Add Reference");
        buttonAddReference.setActionCommand ("Reference");
        buttonAddReference.addActionListener (listenerAdd);

        buttonRun = new JButton (ImageUtil.getImage ("run.gif"));
        buttonRun.setMargin (new Insets (2, 2, 2, 2));
        buttonRun.setFocusable (false);
        buttonRun.setToolTipText ("Run");
        buttonRun.addActionListener (listenerRun);

        buttonExport = new JButton (ImageUtil.getImage ("export.gif"));
        buttonExport.setMargin (new Insets (2, 2, 2, 2));
        buttonExport.setFocusable (false);
        buttonExport.setToolTipText ("Export");
        buttonExport.addActionListener (listenerExport);

        buttonImport = new JButton (ImageUtil.getImage ("import.gif"));
        buttonImport.setMargin (new Insets (2, 2, 2, 2));
        buttonImport.setFocusable (false);
        buttonImport.setToolTipText ("Import");
        buttonImport.addActionListener (listenerImport);

        buttonFilter = new JButton (ImageUtil.getImage ("filter.png"));
        buttonFilter.setMargin (new Insets (2, 2, 2, 2));
        buttonFilter.setFocusable (false);
        buttonFilter.setToolTipText ("Filter Equations");
        buttonFilter.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (System.currentTimeMillis () - menuFilterCanceledAt > 500)  // A really ugly way to prevent the button from re-showing the menu if it was canceled by clicking the button.
                {
                    menuFilter.show (buttonFilter, 0, buttonFilter.getHeight ());
                }
            }
        });

        Lay.BLtg (this,
            "N", Lay.WL ("L",
                buttonAddModel,
                Box.createHorizontalStrut (15),
                buttonAddPart,
                buttonAddVariable,
                buttonAddEquation,
                buttonAddAnnotation,
                buttonAddReference,
                Box.createHorizontalStrut (15),
                buttonFilter,
                Box.createHorizontalStrut (15),
                buttonRun,
                Box.createHorizontalStrut (15),
                buttonExport,
                buttonImport,
                "hgap=5,vgap=1"
            ),
            "C", Lay.p (scrollPane = Lay.sp (tree))
        );

        // Context Menu
        JMenuItem itemAddPart = new JMenuItem ("Add Part", ImageUtil.getImage ("comp.gif"));
        itemAddPart.setActionCommand ("Part");
        itemAddPart.addActionListener (listenerAdd);

        JMenuItem itemAddVariable = new JMenuItem ("Add Variable", ImageUtil.getImage ("delta.png"));
        itemAddVariable.setActionCommand ("Variable");
        itemAddVariable.addActionListener (listenerAdd);

        JMenuItem itemAddEquation = new JMenuItem ("Add Equation", ImageUtil.getImage ("equation.png"));
        itemAddEquation.setActionCommand ("Equation");
        itemAddEquation.addActionListener (listenerAdd);

        JMenuItem itemAddAnnotation = new JMenuItem ("Add Annotation", ImageUtil.getImage ("edit.gif"));
        itemAddAnnotation.setActionCommand ("Annotation");
        itemAddAnnotation.addActionListener (listenerAdd);

        JMenuItem itemAddReference = new JMenuItem ("Add Reference", ImageUtil.getImage ("book.gif"));
        itemAddReference.setActionCommand ("Reference");
        itemAddReference.addActionListener (listenerAdd);

        JMenuItem itemDelete = new JMenuItem ("Delete", ImageUtil.getImage ("remove.gif"));
        itemDelete.addActionListener (listenerDelete);

        menuPopup = new JPopupMenu ();
        menuPopup.add (itemAddPart);
        menuPopup.add (itemAddVariable);
        menuPopup.add (itemAddEquation);
        menuPopup.add (itemAddAnnotation);
        menuPopup.add (itemAddReference);
        menuPopup.addSeparator ();
        menuPopup.add (itemDelete);

        // Filter menu
        int filterLevel = AppData.state.getOrDefaultInt ("PanelModel", "filter", String.valueOf (FilteredTreeModel.PARAM));
        model.setFilterLevel (filterLevel, tree);  // root is still null, so this has no immediate effect
        switch (filterLevel)
        {
            case FilteredTreeModel.REVOKED: buttonFilter.setIcon (iconFilterRevoked); break;
            case FilteredTreeModel.ALL:     buttonFilter.setIcon (iconFilterAll);     break;
            case FilteredTreeModel.PARAM:   buttonFilter.setIcon (iconFilterParam);   break;
            case FilteredTreeModel.LOCAL:   buttonFilter.setIcon (iconFilterLocal);   break;
        }

        JRadioButtonMenuItem itemFilterRevoked = new JRadioButtonMenuItem ("Revoked",    model.filterLevel == FilteredTreeModel.REVOKED);
        itemFilterRevoked.addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterAll     = new JRadioButtonMenuItem ("All",        model.filterLevel == FilteredTreeModel.ALL);
        itemFilterAll    .addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterPublic  = new JRadioButtonMenuItem ("Parameters", model.filterLevel == FilteredTreeModel.PARAM);
        itemFilterPublic .addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterLocal   = new JRadioButtonMenuItem ("Local",      model.filterLevel == FilteredTreeModel.LOCAL);
        itemFilterLocal  .addActionListener (listenerFilter);

        menuFilter = new JPopupMenu ();
        menuFilter.add (itemFilterRevoked);
        menuFilter.add (itemFilterAll);
        menuFilter.add (itemFilterPublic);
        menuFilter.add (itemFilterLocal);
        menuFilter.addPopupMenuListener (new PopupMenuListener ()
        {
            public void popupMenuWillBecomeVisible (PopupMenuEvent e)
            {
            }

            public void popupMenuWillBecomeInvisible (PopupMenuEvent e)
            {
            }

            public void popupMenuCanceled (PopupMenuEvent e)
            {
                menuFilterCanceledAt = System.currentTimeMillis ();
            }
        });

        ButtonGroup groupFilter = new ButtonGroup ();
        groupFilter.add (itemFilterRevoked);
        groupFilter.add (itemFilterAll);
        groupFilter.add (itemFilterPublic);
        groupFilter.add (itemFilterLocal);
    }

    public void loadRootFromDB (MNode doc)
    {
        if (record == doc) return;
        if (record != null)
        {
            tree.stopEditing ();
            // Save tree state for current record, but only if it's better than the previously-saved state.
            if (focusCache.get (record) == null  ||  tree.getSelectionPath () != null) focusCache.put (record, new StoredPath (tree));
        }
        record = doc;
        try
        {
            root = new NodePart (new MPart ((MPersistent) record));
            root.build ();
            root.findConnections ();
            model.setRoot (root);  // triggers repaint, but may be too slow
            updateLock ();
            needsFullRepaint = true;  // next call to repaintSouth() will repaint everything

            StoredPath sp = focusCache.get (record);
            if (sp == null)
            {
                tree.expandRow (0);
                tree.setSelectionRow (0);
            }
            else
            {
                sp.restore (tree);
            }
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
            e.printStackTrace ();
        }
    }

    /**
        Informs us that some other code deleted a document from the DB.
        We only respond if it happens to be on display.
    **/
    public void recordDeleted (MNode doc)
    {
        if (doc != record) return;
        focusCache.remove (record);
        record       = null;
        root         = null;
        model.setRoot (null);  // calls tree.repaint(), but may be too slow
        tree.paintImmediately (scrollPane.getViewport ().getViewRect ());
    }

    public void checkVisible ()
    {
        if (! AppData.models.isVisible (record)) recordDeleted (record);
    }

    public void yieldFocus ()
    {
        if (tree.getSelectionCount () > 0)
        {
            focusCache.put (record, new StoredPath (tree));
            tree.clearSelection ();
        }
    }

    public void updateLock ()
    {
        locked = ! AppData.models.isWriteable (record);
        tree.setEditable (! locked);
    }

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            tree.stopEditing ();
            addAtSelected (e.getActionCommand ());
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            tree.stopEditing ();  // It may seem odd to save a cell just before destroying it, but this gives cleaner UI painting.
            deleteSelected ();
        }
    };

    ActionListener listenerMove = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            tree.stopEditing ();
            moveSelected (Integer.valueOf (e.getActionCommand ()));
        }
    };

    ActionListener listenerRun = new ActionListener ()
    {
        /**
            Fire off a simulation.
            The code below is adapted from gove.sandia.n2a.ui.model.RunDetailPanel, specifically the old-style single-run.
            uiController.prepareAndSubmitRunEnsemble() is the way to set up a run ensemble
        **/
        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
            if (tree.isEditing ())
            {
                tree.stopEditing ();
                mtp.setPreferredFocus (PanelModel.instance, tree);  // Because tree does not reclaim the focus before focus shifts to the run tab.
            }

            String simulatorName = root.source.get ("$metadata", "backend");  // Note that "record" is the raw model, while "root.source" is the collated model.
            final Backend simulator = Backend.getBackend (simulatorName);
            MNode runs = AppData.runs;
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
            final MNode job = runs.set (jobKey, "");  // Create the dir and model doc
            job.merge (root.source);
            job.set ("$inherit", "\"" + record.key () + "\"");
            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

            new Thread ()
            {
                public void run ()
                {
                    try
                    {
                        simulator.start (job);
                    }
                    catch (Exception e)
                    {
                        // TODO: Instead of throwing an exception, simulation should record all errors/warnings in a file in the job dir.
                        e.printStackTrace ();
                    }
                }
            }.start ();

            PanelRun panelRun = (PanelRun) mtp.selectTab ("Runs");
            mtp.setPreferredFocus (panelRun, panelRun.tree);
            panelRun.addNewRun (job);
        }
    };

    ActionListener listenerExport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ExporterFilter extends FileFilter
        {
            public Exporter exporter;

            ExporterFilter (Exporter exporter)
            {
                this.exporter = exporter;
            }

            @Override
            public boolean accept (File f)
            {
                return true;
            }

            @Override
            public String getDescription ()
            {
                return exporter.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            tree.stopEditing ();

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Export \"" + record.key () + "\"");
            ExporterFilter n2a = null;
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Exporter.class);
            for (ExtensionPoint exp : exps)
            {
                ExporterFilter ef = new ExporterFilter ((Exporter) exp);
                fc.addChoosableFileFilter (ef);
                if (ef.exporter.getName ().contains ("N2A")) n2a = ef;
            }
            fc.setAcceptAllFileFilterUsed (false);
            if (n2a != null) fc.setFileFilter (n2a);

            // Display chooser and collect result
            int result = fc.showSaveDialog (MainFrame.instance);

            // Do export
            if (result == JFileChooser.APPROVE_OPTION)
            {
                File path = fc.getSelectedFile ();
                ExporterFilter filter = (ExporterFilter) fc.getFileFilter ();
                filter.exporter.export (record, path);
            }
        }
    };

    ActionListener listenerImport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ImporterFilter extends FileFilter
        {
            public Importer importer;

            ImporterFilter (Importer importer)
            {
                this.importer = importer;
            }

            @Override
            public boolean accept (File f)
            {
                return importer.accept (f);
            }

            @Override
            public String getDescription ()
            {
                return importer.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            tree.stopEditing ();

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Import");
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Importer.class);
            for (ExtensionPoint exp : exps)
            {
                ImporterFilter f = new ImporterFilter ((Importer) exp);
                fc.addChoosableFileFilter (f);
            }

            // Display chooser and collect result
            int result = fc.showOpenDialog (MainFrame.instance);

            // Do import
            if (result == JFileChooser.APPROVE_OPTION)
            {
                File path = fc.getSelectedFile ();
                importFile (path);
            }
        }
    };

    public static void importFile (File path)
    {
        Importer bestImporter = null;
        float    bestP        = 0;
        for (ExtensionPoint exp : PluginManager.getExtensionsForPoint (Importer.class))
        {
            float P = ((Importer) exp).isIn (path);
            if (P > bestP)
            {
                bestP        = P;
                bestImporter = (Importer) exp;
            }
        }
        if (bestImporter != null) bestImporter.process (path);
    }

    ActionListener listenerFilter = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            tree.stopEditing ();

            String action = e.getActionCommand ();
            if (action.equals ("Revoked"))
            {
                model.setFilterLevel (FilteredTreeModel.REVOKED, tree);
                buttonFilter.setIcon (iconFilterRevoked);
            }
            else if (action.equals ("All"))
            {
                model.setFilterLevel (FilteredTreeModel.ALL, tree);
                buttonFilter.setIcon (iconFilterAll);
            }
            else if (action.equals ("Parameters"))
            {
                model.setFilterLevel (FilteredTreeModel.PARAM, tree);
                buttonFilter.setIcon (iconFilterParam);
            }
            else if (action.equals ("Local"))
            {
                model.setFilterLevel (FilteredTreeModel.LOCAL, tree);
                buttonFilter.setIcon (iconFilterLocal);
            }
            AppData.state.set ("PanelModel", "filter", model.filterLevel);
        }
    };

    public NodeBase getSelected ()
    {
        NodeBase result = null;
        TreePath path = tree.getSelectionPath ();
        if (path != null) result = (NodeBase) path.getLastPathComponent ();
        if (result == null) return root;
        return result;
    }

    public void addAtSelected (String type)
    {
        if (locked) return;
        NodeBase selected = getSelected ();
        if (selected == null)  // only happens when root is null
        {
            PanelModel.instance.undoManager.add (new AddDoc ());
            if (type.equals ("Part")) return;  // Since root is itself a Part, don't create another one. For anything else, fall through and add it to the newly-created model.
            selected = root;
        }

        NodeBase editMe = selected.add (type, tree, null);
        if (editMe != null)
        {
            TreePath path = new TreePath (editMe.getPath ());
            tree.scrollPathToVisible (path);
            tree.setSelectionPath (path);
            tree.startEditingAtPath (path);
        }
    }

    public void deleteSelected ()
    {
        if (locked) return;
        NodeBase selected = getSelected ();
        if (selected != null) selected.delete (tree, false);
    }

    public void moveSelected (int direction)
    {
        if (locked) return;
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;

        NodeBase nodeBefore = (NodeBase) path.getLastPathComponent ();
        NodeBase parent     = (NodeBase) nodeBefore.getParent ();
        if (parent instanceof NodePart)  // Only parts support $metadata.gui.order
        {
            // First check if we can move in the filtered (visible) list.
            int indexBefore = model.getIndexOfChild (parent, nodeBefore);
            int indexAfter  = indexBefore + direction;
            if (indexAfter >= 0  &&  indexAfter < model.getChildCount (parent))
            {
                // Then convert to unfiltered indices.
                NodeBase nodeAfter = (NodeBase) model.getChild (parent, indexAfter);
                indexBefore = parent.getIndex (nodeBefore);
                indexAfter  = parent.getIndex (nodeAfter);
                PanelModel.instance.undoManager.add (new Move ((NodePart) parent, indexBefore, indexAfter));
            }
        }
    }

    public void updateVisibility (TreeNode path[])
    {
        if (path.length < 2)
        {
            updateVisibility (path, -1);
        }
        else
        {
            NodeBase c = (NodeBase) path[path.length - 1];
            NodeBase p = (NodeBase) path[path.length - 2];
            int index = p.getIndexFiltered (c);
            updateVisibility (path, index);
        }
    }

    /**
        Ensure that the tree down to the changed node is displayed with correct visibility and override coloring.
        @param path Every node from root to changed node, including changed node itself.
        The trailing nodes are allowed to be disconnected from root in the filtered view of the model,
        and they are allowed to be deleted nodes. Note: deleted nodes will have null parents.
        Deleted nodes should already be removed from tree by the caller, with proper notification.
        @param index Position of the last node in its parent node. Only used if the last node has been deleted.
        A value less than 0 causes selection to shift up to the parent.
    **/
    public void updateVisibility (TreeNode path[], int index)
    {
        // Prepare list of indices for final selection
        int[] selectionIndices = new int[path.length];
        for (int i = 1; i < path.length; i++)
        {
            NodeBase p = (NodeBase) path[i-1];
            NodeBase c = (NodeBase) path[i];
            selectionIndices[i] = model.getIndexOfChild (p, c);  // Could be -1, if c has already been deleted.
        }

        // Adjust visibility
        int inserted = path.length;
        int removed  = path.length;
        int removedIndex = -1;
        for (int i = path.length - 1; i > 0; i--)
        {
            NodeBase p = (NodeBase) path[i-1];
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) continue;  // skip deleted nodes
            int filteredIndex = model.getIndexOfChild (p, c);
            boolean filteredOut = filteredIndex < 0;
            if (c.visible (model.filterLevel))
            {
                if (filteredOut)
                {
                    p.unhide (c, model, false);  // silently adjust the filtering
                    inserted = i; // promise to notify model
                }
            }
            else
            {
                if (! filteredOut)
                {
                    p.hide (c, model, false);
                    removed = i;
                    removedIndex = filteredIndex;
                }
            }
        }

        // update color to indicate override state
        int lastChange = Math.min (inserted, removed);
        for (int i = 1; i < lastChange; i++)
        {
            // Since it is hard to measure current color, just assume everything needs updating.
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) continue;
            model.nodeChanged (c);
            Rectangle bounds = tree.getPathBounds (new TreePath (c.getPath ()));
            if (bounds != null) tree.paintImmediately (bounds);
        }

        if (lastChange < path.length)
        {
            NodeBase p = (NodeBase) path[lastChange-1];
            NodeBase c = (NodeBase) path[lastChange];
            int[] childIndices = new int[1];
            if (inserted < removed)
            {
                childIndices[0] = p.getIndexFiltered (c);
                model.nodesWereInserted (p, childIndices);
            }
            else
            {
                childIndices[0] = removedIndex;
                Object[] childObjects = new Object[1];
                childObjects[0] = c;
                model.nodesWereRemoved (p, childIndices, childObjects);
            }
            repaintSouth (new TreePath (p.getPath ()));
        }

        // select last visible node
        int i = 1;
        for (; i < path.length; i++)
        {
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) break;
            if (! c.visible (model.filterLevel)) break;
        }
        i--;  // Choose the last good node
        NodeBase c = (NodeBase) path[i];
        if (i == path.length - 2)
        {
            index = Math.min (index, model.getChildCount (c) - 1);
            if (index >= 0) c = (NodeBase) model.getChild (c, index);
        }
        else if (i < path.length - 2)
        {
            int childIndex = Math.min (selectionIndices[i+1], model.getChildCount (c) - 1);
            if (childIndex >= 0) c = (NodeBase) model.getChild (c, childIndex);
        }
        TreePath selectedPath = new TreePath (c.getPath ());
        tree.scrollPathToVisible (selectedPath);
        tree.setSelectionPath (selectedPath);
        if (lastChange >= path.length)
        {
            boolean expanded = tree.isExpanded (selectedPath);
            model.nodeStructureChanged (c);  // Should this be more targeted?
            if (expanded) tree.expandPath (selectedPath);
            repaintSouth (selectedPath);
        }
    }

    /**
        Records the current order of nodes in "gui.order", provided that metadata field exists.
        Otherwise, we assume the user doesn't care.
        @param path To the node that changed (added, deleted, moved). In general, this node's
        parent will be the part that is tracking the order of its children.
    **/
    public void updateOrder (TreeNode path[])
    {
        NodePart parent = null;
        for (int i = path.length - 2; i >= 0; i--)
        {
            if (path[i] instanceof NodePart)
            {
                parent = (NodePart) path[i];
                break;
            }
        }
        if (parent == null) return;  // This should never happen, because root of tree is a NodePart.

        // Find $metadata/gui.order for the currently selected node. If it exists, update it.
        // Note that this is a modified version of moveSelected() which does not actually move
        // anything, and which only modifies an existing $metadata/gui.order, not create a new one.
        NodeAnnotations metadataNode = null;
        String order = null;
        Enumeration<?> i = parent.children ();
        while (i.hasMoreElements ())
        {
            NodeBase c = (NodeBase) i.nextElement ();
            String key = c.source.key ();
            if (order == null) order = key;
            else               order = order + "," + key;
            if (key.equals ("$metadata")) metadataNode = (NodeAnnotations) c;
        }
        if (metadataNode == null) return;

        i = metadataNode.children ();
        while (i.hasMoreElements ())
        {
            NodeAnnotation a = (NodeAnnotation) i.nextElement ();
            if (a.source.key ().equals ("gui.order"))
            {
                a.source.set (order);
                FontMetrics fm = a.getFontMetrics (tree);
                metadataNode.updateTabStops (fm);
                model.nodeChanged (a);
                break;
            }
        }
    }

    public void repaintSouth (TreePath path)
    {
        Rectangle node    = tree.getPathBounds (path);
        Rectangle visible = scrollPane.getViewport ().getViewRect ();
        if (! needsFullRepaint  &&  node != null)
        {
            visible.height -= node.y - visible.y;
            visible.y       = node.y;
        }
        needsFullRepaint = false;
        tree.paintImmediately (visible);
    }
}
