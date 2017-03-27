/*
Copyright 2013,2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.plugins.UMFPluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.Move;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.RunPanel;

import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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

import javax.swing.Box;
import javax.swing.ButtonGroup;
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

import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Lay;

public class EquationTreePanel extends JPanel
{
    protected int jobCount = 0;  // for launching jobs

    // Tree
    public    JTree                 tree;
    public    FilteredTreeModel     model;
    public    NodePart              root;
    public    MNode                 record;
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
    protected JButton buttonDelete;
    protected JButton buttonMoveUp;
    protected JButton buttonMoveDown;
    protected JButton buttonRun;
    protected JButton buttonExport;
    protected JButton buttonImport;
    protected JButton buttonFilter;
    protected JPopupMenu menuPopup;
    protected JPopupMenu menuFilter;
    protected long       menuFilterCanceledAt = 0;

    // The main constructor. Most of the real work of setting up the UI is here, including some fairly elaborate listeners.
    public EquationTreePanel ()
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
        tree.setDragEnabled (true);
        tree.setToggleClickCount (0);  // Disable expand/collapse on double-click

        // Remove key bindings that we wish to use for changing order of nodes
        InputMap inputMap = tree.getInputMap ();
        inputMap             .remove (KeyStroke.getKeyStroke ("shift pressed UP"));
        inputMap.getParent ().remove (KeyStroke.getKeyStroke ("shift pressed UP"));
        inputMap             .remove (KeyStroke.getKeyStroke ("shift pressed DOWN"));
        inputMap.getParent ().remove (KeyStroke.getKeyStroke ("shift pressed DOWN"));

        EquationTreeCellRenderer renderer = new EquationTreeCellRenderer ();
        tree.setCellRenderer (renderer);

        final EquationTreeCellEditor editor = new EquationTreeCellEditor (tree, renderer);
        editor.addCellEditorListener (new CellEditorListener ()
        {
            @Override
            public void editingStopped (ChangeEvent e)
            {
                editor.editingNode.applyEdit (tree);
            }

            @Override
            public void editingCanceled (ChangeEvent e)
            {
                // We only get back an empty string if we explicitly set it before editing starts.
                // Certain types of nodes do this when inserting a new instance into the tree, via NodeBase.add()
                // We desire in this case that escape cause the new node to evaporate.
                Object o = editor.editingNode.getUserObject ();
                if (! (o instanceof String)) return;

                NodeBase node   = editor.editingNode;
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

        tree.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton (e)  &&  e.getClickCount () == 2)
                {
                    TreePath path = tree.getClosestPathForLocation (e.getX (), e.getY ());
                    if (path != null) tree.startEditingAtPath (path);
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

        tree.addKeyListener (new KeyAdapter ()
        {
            @Override
            public void keyPressed (KeyEvent e)
            {
                int keycode = e.getKeyCode ();
                if (keycode == KeyEvent.VK_DELETE  ||  keycode == KeyEvent.VK_BACK_SPACE)
                {
                    deleteSelected ();
                }
                else if (keycode == KeyEvent.VK_INSERT)
                {
                    addAtSelected ("");
                }
                else if (keycode == KeyEvent.VK_ENTER)
                {
                    TreePath path = tree.getSelectionPath ();
                    if (path != null)
                    {
                        if (e.isControlDown ()  &&  ! (path.getLastPathComponent () instanceof NodePart)) editor.multiLineRequested = true;
                        tree.startEditingAtPath (path);
                    }
                }
                else if (e.isShiftDown ())
                {
                    if (keycode == KeyEvent.VK_UP)
                    {
                        moveSelected (-1);
                    }
                    else if (keycode == KeyEvent.VK_DOWN)
                    {
                        moveSelected (1);
                    }
                }
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
            public boolean canImport (TransferHandler.TransferSupport xfer)
            {
                return xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferHandler.TransferSupport xfer)
            {
                MNode data = new MVolatile ();
                Schema schema = new Schema ();
                try
                {
                    StringReader reader = new StringReader ((String) xfer.getTransferable ().getTransferData (DataFlavor.stringFlavor));
                    schema.readAll (reader, data);
                }
                catch (IOException | UnsupportedFlavorException e)
                {
                    return false;
                }

                // Determine paste/drop target
                TreePath path;
                if (xfer.isDrop ()) path = ((JTree.DropLocation) xfer.getDropLocation ()).getPath ();
                else                path = tree.getSelectionPath ();
                if (path == null)
                {
                    if (root == null) ModelEditPanel.instance.undoManager.add (new AddDoc ());
                    tree.setSelectionRow (0);
                    path = tree.getSelectionPath ();
                }
                if (xfer.isDrop ()) tree.setSelectionPath (path);
                NodeBase target = (NodeBase) path.getLastPathComponent ();

                ModelEditPanel mep = ModelEditPanel.instance;

                // An import can either be a new node in the tree, or a link (via inheritance) to an existing part.
                // In the case of a link, the part may need to be fully imported if it does not already exist in the db.
                if (schema.type.startsWith ("Clip"))
                {
                    for (MNode child : data)
                    {
                        NodeBase added = target.add (schema.type.substring (4), tree, child);
                        if (added == null) return false;
                    }
                    return true;
                }
                else if (schema.type.equals ("Part"))
                {
                    for (MNode child : data)  // There could be multiple parts.
                    {
                        // Ensure the part is in our db
                        String key = child.key ();
                        if (AppData.models.child (key) == null) mep.undoManager.add (new AddDoc (key, child));

                        // Create an include-style part
                        MNode include = new MVolatile ();  // Note the empty key. This enables AddPart to generate a name.
                        include.merge (child);  // TODO: What if this brings in a $inherit line, and that line does not match the $inherit line in the source part? One possibility is to add the new values to the end of the $inherit line created below.
                        include.set ("$inherit", "\"" + key + "\"");
                        NodeBase added = target.add ("Part", tree, include);
                        if (added == null) return false;
                    }
                    return true;
                }
                return false;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY_OR_MOVE;
            }

            class TransferableNode extends StringSelection
            {
                public List<String> path;

                public TransferableNode (String data, NodeBase source)
                {
                    super (data);
                    path = source.getKeyPath ();
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
            }

            protected Transferable createTransferable (JComponent comp)
            {
                NodeBase node = getSelected ();
                if (node == null) return null;
                MVolatile copy = new MVolatile ();
                node.copy (copy);

                Schema schema = new Schema (1, "Clip" + node.getTypeName ());
                StringWriter writer = new StringWriter ();
                try
                {
                    schema.write (writer);
                    for (MNode c : copy) c.write (writer);
                    writer.close ();

                    return new TransferableNode (writer.toString (), node);
                }
                catch (IOException e)
                {
                }

                return null;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                if (action == MOVE  &&  data instanceof TransferableNode)
                {
                    // It is possible for the node to be removed from the tree before we get to it.
                    // For example, a local drop of an $inherit node will cause the tree to rebuild.
                    NodeBase node = ((TransferableNode) data).getSource ();
                    if (node != null) node.delete (tree, false);
                }
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
                if (! e.isTemporary ()  &&  ! tree.isEditing ())  // The shift to the editing component appears as a loss of focus.
                {
                    if (record != null) focusCache.put (record, new StoredPath (tree));
                    tree.clearSelection ();
                }
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
                ModelEditPanel.instance.undoManager.add (new AddDoc ());
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

        buttonAddEquation = new JButton (ImageUtil.getImage ("equation.png"));
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

        buttonDelete = new JButton (ImageUtil.getImage ("remove.gif"));
        buttonDelete.setMargin (new Insets (2, 2, 2, 2));
        buttonDelete.setFocusable (false);
        buttonDelete.setToolTipText ("Delete");
        buttonDelete.addActionListener (listenerDelete);

        buttonMoveUp = new JButton (ImageUtil.getImage ("up.gif"));
        buttonMoveUp.setMargin (new Insets (2, 2, 2, 2));
        buttonMoveUp.setFocusable (false);
        buttonMoveUp.setToolTipText ("Move Up");
        buttonMoveUp.setActionCommand ("-1");
        buttonMoveUp.addActionListener (listenerMove);

        buttonMoveDown = new JButton (ImageUtil.getImage ("down.gif"));
        buttonMoveDown.setMargin (new Insets (2, 2, 2, 2));
        buttonMoveDown.setFocusable (false);
        buttonMoveDown.setToolTipText ("Move Down");
        buttonMoveDown.setActionCommand ("1");
        buttonMoveDown.addActionListener (listenerMove);

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
            "N", Lay.FL ("L",
                buttonAddModel,
                Box.createHorizontalStrut (15),
                buttonAddPart,
                buttonAddVariable,
                buttonAddEquation,
                buttonAddAnnotation,
                buttonAddReference,
                Box.createHorizontalStrut (15),
                buttonDelete,
                Box.createHorizontalStrut (15),
                buttonMoveUp,
                buttonMoveDown,
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
        JRadioButtonMenuItem itemFilterAll = new JRadioButtonMenuItem ("All", false);
        itemFilterAll.addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterPublic = new JRadioButtonMenuItem ("Public", true);
        model.setFilterLevel (FilteredTreeModel.PUBLIC, tree);  // root is still null, so this has no immediate effect
        itemFilterPublic.addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterLocal = new JRadioButtonMenuItem ("Local", false);
        itemFilterLocal.addActionListener (listenerFilter);

        menuFilter = new JPopupMenu ();
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
        groupFilter.add (itemFilterAll);
        groupFilter.add (itemFilterPublic);
        groupFilter.add (itemFilterLocal);
    }

    public void loadRootFromDB (MNode doc)
    {
        if (record == doc) return;
        record = doc;
        try
        {
            root = new NodePart (new MPart ((MPersistent) record));
            root.build ();
            root.findConnections ();
            model.setRoot (root);  // triggers repaint, but may be too slow
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

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            addAtSelected (e.getActionCommand ());
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            deleteSelected ();
        }
    };

    ActionListener listenerMove = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
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

            String simulatorName = record.get ("$metadata", "backend");
            final Backend simulator = UMFPluginManager.getBackend (simulatorName);
            MNode runs = AppData.runs;
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
            runs.set (jobKey, "");  // Create the dir and model doc
            final MNode job = runs.child (jobKey);
            job.merge (root.source);
            job.set ("$inherit", "\"" + record.key () + "\"");
            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

            new Thread ()
            {
                public void run ()
                {
                    try
                    {
                        simulator.execute (job);
                    }
                    catch (Exception e)
                    {
                        // TODO: Instead of throwing an exception, simulation should record all errors/warnings in a file in the job dir.
                        e.printStackTrace ();
                    }
                }
            }.start ();

            MainTabbedPane mtp = (MainTabbedPane) MainFrame.getInstance ().tabs;
            RunPanel panelRun = (RunPanel) mtp.selectTab ("Runs");
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
            int result = fc.showSaveDialog (MainFrame.getInstance ());

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
                return true;
            }

            @Override
            public String getDescription ()
            {
                return importer.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Import");
            ImporterFilter n2a = null;
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Importer.class);
            for (ExtensionPoint exp : exps)
            {
                ImporterFilter f = new ImporterFilter ((Importer) exp);
                fc.addChoosableFileFilter (f);
                if (f.importer.getName ().contains ("N2A")) n2a = f;
            }
            fc.setAcceptAllFileFilterUsed (false);
            if (n2a != null) fc.setFileFilter (n2a);

            // Display chooser and collect result
            int result = fc.showOpenDialog (MainFrame.getInstance ());

            // Do import
            if (result == JFileChooser.APPROVE_OPTION)
            {
                File path = fc.getSelectedFile ();
                ImporterFilter filter = (ImporterFilter) fc.getFileFilter ();
                filter.importer.process (path);
            }
        }
    };

    ActionListener listenerFilter = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            String action = e.getActionCommand ();
            if      (action.equals ("All"   )) model.setFilterLevel (FilteredTreeModel.ALL,    tree);
            else if (action.equals ("Public")) model.setFilterLevel (FilteredTreeModel.PUBLIC, tree);
            else if (action.equals ("Local" )) model.setFilterLevel (FilteredTreeModel.LOCAL,  tree);
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
        NodeBase selected = getSelected ();
        if (selected == null)  // only happens when root is null
        {
            ModelEditPanel.instance.undoManager.add (new AddDoc ());
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
        NodeBase selected = getSelected ();
        if (selected != null) selected.delete (tree, false);
    }

    public void moveSelected (int direction)
    {
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
                ModelEditPanel.instance.undoManager.add (new Move ((NodePart) parent, indexBefore, indexAfter));
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
