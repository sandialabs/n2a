/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.plugins.UMFPluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.RunPanel;

import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
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
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Lay;

public class EquationTreePanel extends JPanel
{
    protected ModelEditPanel modelPanel;
    protected MNode record;
    protected int jobCount = 0;  // for launching jobs

    // Tree
    protected JTree                 tree;
    protected FilteredTreeModel     model;
    protected NodePart              root;
    protected JScrollPane           scrollPane;
    protected Map<MNode,StoredPath> focusCache = new HashMap<MNode,StoredPath> ();

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
    public EquationTreePanel (ModelEditPanel container)
    {
        modelPanel = container;

        model = new FilteredTreeModel (null);
        tree  = new JTree (model)
        {
            @Override
            public String convertValueToText (Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                if (value == null) return "";
                return ((NodeBase) value).getText (expanded);
            }
        };

        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection. It only makes deletes and moves more complicated.
        tree.setEditable (true);
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
                NodeBase parent = (NodeBase) editor.editingNode.getParent ();  // Could be null if we edit the root node.
                TreePath parentPath = null;
                int index = 0;
                if (parent != null)
                {
                    parentPath = new TreePath (parent.getPath ());
                    index = model.getIndexOfChild (parent, editor.editingNode) - 1;
                }

                editor.editingNode.applyEdit (tree);

                if (editor.editingNode == root) modelPanel.recordRenamed ();  // The only real reason to edit root is to change the name. However, it may also have stayed the same. We don't check for that.

                TreePath path = tree.getSelectionPath ();
                if (path == null) path = updateSelection (parentPath, index);  // If we lose the selection, most likely applyEdit() deleted the node, and that function assumes the caller handles selection.
                updateOrder ();
                if (parent != null) updateOverrides (path);
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
                    // Similar behavior to deleteSelected(), but set selection to previous node, since it is likely that this node was added from there.
                    int index = model.getIndexOfChild (parent, node) - 1;

                    node.delete (tree);

                    TreePath path = updateSelection (new TreePath (parent.getPath ()), index);
                    updateOrder ();
                    updateOverrides (path);
                    repaintSouth (path);
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
                    deleteSelected (e.isControlDown ());
                }
                else if (keycode == KeyEvent.VK_INSERT)
                {
                    addAtSelected ("");
                }
                else if (keycode == KeyEvent.VK_ENTER)
                {
                    editSelected ();
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
            public boolean canImport (TransferHandler.TransferSupport support)
            {
                if (! support.isDrop ()) return false;  // could also support pasting models from the clipboard, 
                if (! support.isDataFlavorSupported (DataFlavor.stringFlavor)) return false;
                return true;
            }

            public boolean importData (TransferHandler.TransferSupport support)
            {
                // Get key for dropped part
                String key;
                try
                {
                    key = (String) support.getTransferable().getTransferData (DataFlavor.stringFlavor);
                }
                catch (Exception e)
                {
                    return false;
                }
                key = key.split ("=", 2)[0];  // data actually contains name=path; rather than hack the search list, simply extract the key

                // Determine container part
                TreePath path = ((JTree.DropLocation) support.getDropLocation ()).getPath ();
                if (path == null)
                {
                    if (root == null) createNewModel ();
                    tree.setSelectionRow (0);
                    path = tree.getSelectionPath ();
                }
                else
                {
                    tree.setSelectionPath (path);
                }
                NodeBase dropTarget = (NodeBase) path.getLastPathComponent ();
                NodeBase added = dropTarget.addDnD (key, tree);
                if (added == null) return false;

                path = new TreePath (added.getPath ());
                tree.scrollPathToVisible (path);
                tree.setSelectionPath (path);
                tree.requestFocusInWindow ();  // just in case the search panel is still selected
                // Should we go into edit mode instead?

                updateOrder ();

                modelPanel.searchHideSelection ();  // because DnD highlights a selection without triggering focus notifications

                return true;
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
                createNewModel ();
                tree.requestFocusInWindow ();
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
        record = doc;
        try
        {
            root = new NodePart (new MPart ((MPersistent) record));
            root.build ();
            root.findConnections ();
            model.setRoot (root);

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
            boolean shift = (e.getModifiers () & ActionEvent.CTRL_MASK) != 0;
            deleteSelected (shift);
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
            runs.set ("", jobKey);  // Create the dir and model doc
            final MNode job = runs.child (jobKey);
            job.merge (root.source);
            job.set ("\"" + record.key () + "\"", "$inherit");
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
            public JComponent accessory;  ///< Store the accessory so it can retain state between changes in file filter.

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

            public JComponent getAccessory (JFileChooser fc)
            {
                if (accessory == null) accessory = exporter.getAccessory (fc);
                return accessory;
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
            fc.addPropertyChangeListener (JFileChooser.FILE_FILTER_CHANGED_PROPERTY, new PropertyChangeListener ()
            {
                public void propertyChange (PropertyChangeEvent arg0)
                {
                    fc.setAccessory (((ExporterFilter) fc.getFileFilter ()).getAccessory (fc));
                    fc.revalidate ();
                }
            });
            fc.setAcceptAllFileFilterUsed (false);
            if (n2a != null) fc.setFileFilter (n2a);

            // Display chooser and collect result
            int result = fc.showSaveDialog (MainFrame.getInstance ());

            // Do export
            if (result == JFileChooser.APPROVE_OPTION)
            {
                File path = fc.getSelectedFile ();
                ExporterFilter filter = (ExporterFilter) fc.getFileFilter ();
                filter.exporter.export (record, path, filter.accessory);
            }
        }
    };

    ActionListener listenerImport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ImporterFilter extends FileFilter
        {
            public Importer importer;
            public JComponent accessory;  ///< Store the accessory so it can retain state between changes in file filter.

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

            public JComponent getAccessory (JFileChooser fc)
            {
                if (accessory == null) accessory = importer.getAccessory (fc);
                return accessory;
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
            fc.addPropertyChangeListener (JFileChooser.FILE_FILTER_CHANGED_PROPERTY, new PropertyChangeListener ()
            {
                public void propertyChange (PropertyChangeEvent arg0)
                {
                    fc.setAccessory (((ImporterFilter) fc.getFileFilter ()).getAccessory (fc));
                    fc.revalidate ();
                }
            });
            fc.setAcceptAllFileFilterUsed (false);
            if (n2a != null) fc.setFileFilter (n2a);

            // Display chooser and collect result
            int result = fc.showOpenDialog (MainFrame.getInstance ());

            // Do import
            if (result == JFileChooser.APPROVE_OPTION)
            {
                File path = fc.getSelectedFile ();
                ImporterFilter filter = (ImporterFilter) fc.getFileFilter ();
                filter.importer.process (path, filter.accessory);
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
            createNewModel ();
            if (type.equals ("Part")) return;  // Since root is itself a Part, don't create another one. For anything else, fall through and add it to the newly-created model.
            selected = root;
        }

        NodeBase editMe = selected.add (type, tree);
        if (editMe != null)
        {
            TreePath path = new TreePath (editMe.getPath ());
            tree.scrollPathToVisible (path);
            tree.setSelectionPath (path);
            tree.startEditingAtPath (path);
        }
    }

    public void createNewModel ()
    {
        MNode newModel = createNewModel ("New Model");
        loadRootFromDB (newModel);
    }

    public MNode createNewModel (String name)
    {
        MNode models = AppData.models;
        MNode result = models.child (name);
        if (result == null)
        {
            result = models.set ("", name);
        }
        else
        {
            name += " ";
            int suffix = 2;
            while (true)
            {
                if (result.length () == 0) break;  // no children, so still a virgin
                result = models.child (name + suffix);
                if (result == null)
                {
                    result = models.set ("", name + suffix);
                    break;
                }
                suffix++;
            }
        }

        modelPanel.searchInsertDoc (result);
        return result;
    }

    public void editSelected ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path != null) tree.startEditingAtPath (path);
    }

    public void deleteSelected (boolean controlKeyDown)
    {
        NodeBase selected = getSelected ();
        if (selected != null)
        {
            if (selected.isRoot ())
            {
                if (controlKeyDown)  // Only delete the root (entire document) if the user does something extra to say they really mean it.
                {
                    focusCache.remove (record);
                    modelPanel.searchRemoveDoc (record);
                    ((MDoc) record).delete ();
                    record       = null;
                    root         = null;
                    model.setRoot (null);
                }
            }
            else
            {
                NodeBase parent = (NodeBase) selected.getParent ();
                TreePath path = new TreePath (parent.getPath ());
                int index = parent.getIndex (selected);

                selected.delete (tree);

                path = updateSelection (path, index);
                updateOrder ();
                updateOverrides (path);
                repaintSouth (path);
            }
        }
    }

    public void moveSelected (int direction)
    {
        TreePath path = tree.getSelectionPath ();
        if (path != null)
        {
            NodeBase node   = (NodeBase) path.getLastPathComponent ();
            NodeBase parent = (NodeBase) node.getParent ();
            if (parent instanceof NodePart)  // Only parts support $metadata.gui.order
            {
                int index = model.getIndexOfChild (parent, node) + direction;
                if (index >= 0  &&  index < model.getChildCount (parent))
                {
                    model.removeNodeFromParent (node);
                    model.insertNodeInto (node, parent, index);

                    NodeAnnotations metadataNode = null;
                    String order = null;
                    Enumeration i = parent.children ();  // unfiltered, since we want to store order of all nodes, not just visible ones
                    while (i.hasMoreElements ())
                    {
                        NodeBase c = (NodeBase) i.nextElement ();
                        String key = c.source.key ();
                        if (order == null) order = key;
                        else               order = order + "," + key;
                        if (key.equals ("$metadata")) metadataNode = (NodeAnnotations) c;
                    }

                    MPart metadataPart = null;
                    if (metadataNode == null)
                    {
                        metadataPart = (MPart) parent.source.set ("", "$metadata");
                        metadataNode = new NodeAnnotations (metadataPart);
                        model.insertNodeIntoUnfiltered (metadataNode, parent, 0);
                        if (order.isEmpty ()) order = "$metadata";
                        else                  order = "$metadata" + "," + order;
                    }
                    NodeAnnotation orderNode = null;
                    i = metadataNode.children ();
                    while (i.hasMoreElements ())
                    {
                        NodeAnnotation a = (NodeAnnotation) i.nextElement ();
                        if (a.source.key ().equals ("gui.order"))
                        {
                            orderNode = a;
                            break;
                        }
                    }
                    if (orderNode == null)
                    {
                        orderNode = new NodeAnnotation ((MPart) metadataNode.source.set (order, "gui.order"));
                        model.insertNodeIntoUnfiltered (orderNode, metadataNode, metadataNode.getChildCount ());
                    }
                    else
                    {
                        orderNode.source.set (order);
                        FontMetrics fm = orderNode.getFontMetrics (tree);
                        // Don't need to call updateColumnWidths(), because size of string "gui.order" is constant.
                        metadataNode.updateTabStops (fm);  // necessary to get tab stops, since we don't store them
                        model.nodeChanged (orderNode);
                    }

                    path = new TreePath (model.getPathToRoot (node));
                    tree.setSelectionPath (path);
                }
            }
        }
    }

    /**
        Re-establish a reasonable selection after a node is deleted.
    **/
    public TreePath updateSelection (TreePath path, int index)
    {
        if (path == null  ||  path.getPathCount () == 0)
        {
            tree.setSelectionRow (0);
            return tree.getSelectionPath ();
        }

        // Verify that parent node is still in tree.
        // In some cases (such as metadata), deleting the only remaining node can also cause the deletion of the parent.
        NodeBase parent = (NodeBase) path.getLastPathComponent ();
        if (parent != root  &&  (parent.getParent () == null  ||  ! parent.visible (model.filterLevel)))  // Parent was removed as well.
        {
            return updateSelection (path.getParentPath (), -1);
        }

        index = Math.min (index, model.getChildCount (parent) - 1);
        if (index >= 0) path = new TreePath (((NodeBase) model.getChild (parent, index)).getPath ());
        tree.setSelectionPath (path);
        return path;
    }

    /**
        Records the current order of nodes in "gui.order", provided that metadata field exists.
        Otherwise, we assume the user doesn't care.
    **/
    public void updateOrder ()
    {
        // Find $metadata/gui.order for the currently selected node. If it exists, update it.
        // Note that this is a modified version of moveSelected() which does not actually move
        // anything, and which only modifies an existing $metadata/gui.order, not create a new one.
        TreePath path = tree.getSelectionPath ();
        if (path != null)
        {
            NodeBase node   = (NodeBase) path.getLastPathComponent ();
            NodeBase parent = (NodeBase) node.getParent ();
            if (parent instanceof NodePart)  // Only parts support $metadata.gui.order
            {
                NodeAnnotations metadataNode = null;
                String order = null;
                Enumeration i = parent.children ();
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
                        return;
                    }
                }
            }
        }
    }

    /**
        Redisplay any nodes that might have been reset to non-overridden state.
    **/
    public void updateOverrides (TreePath path)
    {
        for (int i = path.getPathCount () - 1; i >= 0; i--)
        {
            NodeBase n = (NodeBase) path.getPathComponent (i);
            if (! n.source.isFromTopDocument ()) model.nodeChanged (n);
        }
    }

    public void repaintSouth (TreePath path)
    {
        Rectangle node    = tree.getPathBounds (path);
        Rectangle visible = scrollPane.getViewport ().getViewRect ();
        visible.height -= node.y - visible.y;
        visible.y       = node.y;
        tree.paintImmediately (visible);
    }
}
