/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MDoc;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.Backend;
import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import gov.sandia.umf.platform.plugins.extpoints.Importer;
import gov.sandia.umf.platform.ui.MainFrame;
import gov.sandia.umf.platform.ui.MainTabbedPane;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.jobs.RunPanel;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
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
import java.util.EventObject;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import replete.gui.controls.IconButton;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Lay;

public class EquationTreePanel extends JPanel
{
    protected MNode record;
    protected int jobCount = 0;  // for launching jobs

    // Tree & Its Model
    protected JTree            tree;
    protected DefaultTreeModel model;
    protected NodePart         root;
    protected JScrollPane      scrollPane;
    protected int              lastSelectedRow = -1;
    protected SearchPanel      panelSearch;  // reference to other side of our panel pair, so we can send updates (alternative to a listener arrangement)

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
    protected JPopupMenu menuPopup;

    /**
        Extends the standard tree cell renderer to get icon and text style from NodeBase.
        This is the core code that makes NodeBase work as a tree node representation.
    **/
    public class NodeRenderer extends DefaultTreeCellRenderer
    {
        protected Font  baseFont;
        protected float baseFontSize;

        @Override
        public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
        {
            super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

            if (baseFont == null)
            {
                baseFont = getFont ().deriveFont (Font.PLAIN);
                baseFontSize = baseFont.getSize2D ();
            }

            NodeBase n = (NodeBase) value;
            setFont (getFontFor (n));
            setForeground (n.getForegroundColor ());
            setIcon (getIconFor (n, expanded, leaf));

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

        public Font getFontFor (NodeBase node)
        {
            int   style = node.getFontStyle ();
            float scale = node.getFontScale ();
            if (style != Font.PLAIN  ||  scale != 1) return baseFont.deriveFont (style, baseFontSize * scale);
            return baseFont;
        }
    }

    /**
        Extends the standard tree cell editor to cooperate with NodeBase icon and text styles.
        Adds a few other nice behaviors:
        * Makes cell editing act more like a text document.
          - No visible border
          - Extends full width of tree panel
        * Selects the value portion of an equation, facilitating the user to make simple changes.
    **/
    public class NodeEditor extends DefaultTreeCellEditor
    {
        public NodeBase         editingNode;
        public DefaultTextField textField;

        public NodeEditor (JTree tree, DefaultTreeCellRenderer renderer)
        {
            super (tree, renderer);
        }

        @Override
        public Font getFont ()
        {
            return ((NodeRenderer) renderer).getFontFor (editingNode);
        }

        @Override
        public boolean isCellEditable (EventObject e)
        {
            if (! super.isCellEditable (e)) return false;
            if (lastPath == null) return false;
            Object o = lastPath.getLastPathComponent ();
            if (! (o instanceof NodeBase)) return false;
            editingNode = (NodeBase) o;
            return editingNode.allowEdit ();
        }

        /**
            Set editingIcon and offset.
        **/
        @Override
        protected void determineOffset (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
        {
            editingIcon = ((NodeRenderer) renderer).getIconFor ((NodeBase) value, expanded, leaf);
            offset = renderer.getIconTextGap () + editingIcon.getIconWidth ();
        }

        @Override
        protected TreeCellEditor createTreeCellEditor ()
        {
            textField = new DefaultTextField (new EmptyBorder (0, 0, 0, 0))
            {
                @Override
                public Dimension getPreferredSize ()
                {
                    Dimension result = super.getPreferredSize ();
                    result.width = Math.max (result.width, tree.getWidth () - (editingNode.getLevel () + 1) * offset - 5);  // The extra 5 pixels come from DefaultTreeCellEditor.EditorContainer.getPreferredSize()
                    return result;
                }
            };

            final UndoManager undo = new UndoManager ();
            textField.getDocument ().addUndoableEditListener (undo);
            textField.getActionMap ().put ("Undo", new AbstractAction ("Undo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undo.undo ();}
                    catch (CannotUndoException e) {}
                }
            });
            textField.getActionMap ().put ("Redo", new AbstractAction ("Redo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undo.redo();}
                    catch (CannotRedoException e) {}
                }
            });
            textField.getInputMap ().put (KeyStroke.getKeyStroke ("control Z"), "Undo");
            textField.getInputMap ().put (KeyStroke.getKeyStroke ("control Y"), "Redo");
            textField.getInputMap ().put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");

            textField.addFocusListener (new FocusListener ()
            {
                @Override
                public void focusGained (FocusEvent e)
                {
                    // Analyze text of control and set an appropriate selection
                    String text = textField.getText ();
                    int equals = text.indexOf ('=');
                    int at     = text.indexOf ('@');
                    if (equals >= 0  &&  equals < text.length () - 1)  // also check for combiner character
                    {
                        if (":+*/<>".indexOf (text.charAt (equals + 1)) >= 0) equals++;
                    }
                    if (at < 0)  // no condition
                    {
                        if (equals >= 0)  // a single-line equation
                        {
                            textField.setCaretPosition (text.length ());
                            textField.moveCaretPosition (equals + 1);
                        }
                        else  // A part name
                        {
                            textField.setCaretPosition (text.length ());
                        }
                    }
                    else if (equals > at)  // a multi-conditional line that has "=" in the condition
                    {
                        textField.setCaretPosition (0);
                        textField.moveCaretPosition (at);
                    }
                    else  // a single-line equation with a condition
                    {
                        textField.setCaretPosition (equals + 1);
                        textField.moveCaretPosition (at);
                    }
                }

                @Override
                public void focusLost (FocusEvent e)
                {
                }
            });

            DefaultCellEditor result = new DefaultCellEditor (textField)
            {
                @Override
                public Component getTreeCellEditorComponent (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
                {
                    // Lie about the expansion state, to force NodePart to return the true name of the part, without parenthetical info about type.
                    return super.getTreeCellEditorComponent (tree, value, isSelected, true, leaf, row);
                }
            };
            result.setClickCountToStart (1);
            return result;
        }
    }

    // The main constructor. Most of the real work of setting up the UI is here, including some fairly elaborate listeners.
    public EquationTreePanel ()
    {
        model = new DefaultTreeModel (null);
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

        // Remove key bindings that we wish to use for changing order of nodes
        InputMap inputMap = tree.getInputMap ();
        inputMap             .remove (KeyStroke.getKeyStroke ("shift pressed UP"));
        inputMap.getParent ().remove (KeyStroke.getKeyStroke ("shift pressed UP"));
        inputMap             .remove (KeyStroke.getKeyStroke ("shift pressed DOWN"));
        inputMap.getParent ().remove (KeyStroke.getKeyStroke ("shift pressed DOWN"));

        NodeRenderer renderer = new NodeRenderer ();
        tree.setCellRenderer (renderer);

        final NodeEditor editor = new NodeEditor (tree, renderer);
        editor.addCellEditorListener (new CellEditorListener ()
        {
            @Override
            public void editingStopped (ChangeEvent e)
            {
                NodeBase parent = (NodeBase) editor.editingNode.getParent ();  // Could be null if we edit the root node.
                int index = 0;
                if (parent != null) index = parent.getIndex (editor.editingNode) - 1;

                editor.editingNode.applyEdit (tree);

                if (editor.editingNode == root) panelSearch.list.repaint ();  // possible name change on model

                TreePath path = tree.getSelectionPath ();
                if (path == null)  // If we lose the selection, most likely applyEdit() deleted the node, and that function assumes the caller handles selection.
                {
                    if (parent == null)
                    {
                        tree.setSelectionRow (0);
                    }
                    else
                    {
                        index = Math.min (index, parent.getChildCount () - 1);
                        if (index < 0) path = new TreePath (                          parent.getPath ());
                        else           path = new TreePath (((DefaultMutableTreeNode) parent.getChildAt (index)).getPath ());
                        tree.setSelectionPath (path);
                    }
                }

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
                if (((String) o).isEmpty ())
                {
                    // Similar behavior to deleteSelected(), but set selection to previous node, since it is likely that this node was added from there.
                    NodeBase parent = (NodeBase) editor.editingNode.getParent ();
                    int index = parent.getIndex (editor.editingNode) - 1;

                    editor.editingNode.delete (tree);

                    index = Math.min (index, parent.getChildCount () - 1);
                    TreePath path;
                    if (index < 0) path = new TreePath (                          parent.getPath ());
                    else           path = new TreePath (((DefaultMutableTreeNode) parent.getChildAt (index)).getPath ());
                    tree.setSelectionPath (path);

                    updateOrder ();
                    updateOverrides (path);
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

                panelSearch.hideSelection ();  // because DnD highlights a selection without triggering focus notifications

                return true;
            }  
        });

        tree.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                if (tree.getSelectionCount () < 1)
                {
                    if (lastSelectedRow < 0  ||  lastSelectedRow >= tree.getRowCount ()) tree.setSelectionRow (0);
                    else                                                                 tree.setSelectionRow (lastSelectedRow);
                }
            }

            public void focusLost (FocusEvent e)
            {
                if (! e.isTemporary ()  &&  ! tree.isEditing ())  // The shift to the editing component appears as a loss of focus.
                {
                    int[] rows = tree.getSelectionRows ();
                    if (rows != null  &&  rows.length > 0) lastSelectedRow = rows[0];
                    tree.clearSelection ();
                }
            }
        });

        // Side Buttons

        buttonAddModel = new IconButton (ImageUtil.getImage ("explore.gif"), 2);
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

        buttonAddPart = new IconButton (ImageUtil.getImage ("comp.gif"), 2);
        buttonAddPart.setFocusable (false);
        buttonAddPart.setToolTipText ("Add Part");
        buttonAddPart.setActionCommand ("Part");
        buttonAddPart.addActionListener (addListener);

        buttonAddVariable = new IconButton (ImageUtil.getImage ("delta.png"), 2);
        buttonAddVariable.setFocusable (false);
        buttonAddVariable.setToolTipText ("Add Variable");
        buttonAddVariable.setActionCommand ("Variable");
        buttonAddVariable.addActionListener (addListener);

        buttonAddEquation = new IconButton (ImageUtil.getImage ("equation.png"), 2);
        buttonAddEquation.setFocusable (false);
        buttonAddEquation.setToolTipText ("Add Equation");
        buttonAddEquation.setActionCommand ("Equation");
        buttonAddEquation.addActionListener (addListener);

        buttonAddAnnotation = new IconButton (ImageUtil.getImage ("edit.gif"), 2);
        buttonAddAnnotation.setFocusable (false);
        buttonAddAnnotation.setToolTipText ("Add Annotation");
        buttonAddAnnotation.setActionCommand ("Annotation");
        buttonAddAnnotation.addActionListener (addListener);

        buttonAddReference = new IconButton (ImageUtil.getImage ("book.gif"), 2);
        buttonAddReference.setFocusable (false);
        buttonAddReference.setToolTipText ("Add Reference");
        buttonAddReference.setActionCommand ("Reference");
        buttonAddReference.addActionListener (addListener);

        buttonDelete = new IconButton (ImageUtil.getImage ("remove.gif"), 2);
        buttonDelete.setFocusable (false);
        buttonDelete.setToolTipText ("Delete");
        buttonDelete.addActionListener (deleteListener);

        buttonMoveUp = new IconButton (ImageUtil.getImage ("up.gif"), 2);
        buttonMoveUp.setFocusable (false);
        buttonMoveUp.setToolTipText ("Move Up");
        buttonMoveUp.setActionCommand ("-1");
        buttonMoveUp.addActionListener (moveListener);

        buttonMoveDown = new IconButton (ImageUtil.getImage ("down.gif"), 2);
        buttonMoveDown.setFocusable (false);
        buttonMoveDown.setToolTipText ("Move Down");
        buttonMoveDown.setActionCommand ("1");
        buttonMoveDown.addActionListener (moveListener);

        buttonRun = new IconButton (ImageUtil.getImage ("run.gif"), 2);
        buttonRun.setFocusable (false);
        buttonRun.setToolTipText ("Run");
        buttonRun.addActionListener (runListener);

        buttonExport = new IconButton (ImageUtil.getImage ("export.gif"), 2);
        buttonExport.setFocusable (false);
        buttonExport.setToolTipText ("Export");
        buttonExport.addActionListener (exportListener);

        buttonImport = new IconButton (ImageUtil.getImage ("import.gif"), 2);
        buttonImport.setFocusable (false);
        buttonImport.setToolTipText ("Import");
        buttonImport.addActionListener (importListener);

        // Context Menus
        JMenuItem menuAddPart = new JMenuItem ("Add Part", ImageUtil.getImage ("comp.gif"));
        menuAddPart.setActionCommand ("Part");
        menuAddPart.addActionListener (addListener);

        JMenuItem menuAddVariable = new JMenuItem ("Add Variable", ImageUtil.getImage ("delta.png"));
        menuAddVariable.setActionCommand ("Variable");
        menuAddVariable.addActionListener (addListener);

        JMenuItem menuAddEquation = new JMenuItem ("Add Equation", ImageUtil.getImage ("equation.png"));
        menuAddEquation.setActionCommand ("Equation");
        menuAddEquation.addActionListener (addListener);

        JMenuItem menuAddAnnotation = new JMenuItem ("Add Annotation", ImageUtil.getImage ("edit.gif"));
        menuAddAnnotation.setActionCommand ("Annotation");
        menuAddAnnotation.addActionListener (addListener);

        JMenuItem menuAddReference = new JMenuItem ("Add Reference", ImageUtil.getImage ("book.gif"));
        menuAddReference.setActionCommand ("Reference");
        menuAddReference.addActionListener (addListener);

        JMenuItem menuDelete = new JMenuItem ("Delete", ImageUtil.getImage ("remove.gif"));
        menuDelete.addActionListener (deleteListener);

        menuPopup = new JPopupMenu ();
        menuPopup.add (menuAddPart);
        menuPopup.add (menuAddVariable);
        menuPopup.add (menuAddEquation);
        menuPopup.add (menuAddAnnotation);
        menuPopup.add (menuAddReference);
        menuPopup.addSeparator ();
        menuPopup.add (menuDelete);

        Lay.BLtg (this,
            "C", Lay.p (scrollPane = Lay.sp (tree)),
            "E", Lay.BxL ("Y",
                Lay.BL (buttonAddModel,      "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddPart,       "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddVariable,   "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddEquation,   "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddAnnotation, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddReference,  "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonDelete,        "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonMoveUp,        "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonMoveDown,      "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonRun,           "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonExport,        "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonImport,        "eb=5b,alignx=0.5,maxH=20"),
                Box.createVerticalGlue ()
            )
        );
    }

    public void setEquations (final MNode eqs)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                loadRootFromDB (eqs);
            }
        });
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
            tree.expandRow (0);
            tree.setSelectionRow (0);
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
            e.printStackTrace ();
        }
    }

    ActionListener addListener = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            addAtSelected (e.getActionCommand ());
        }
    };

    ActionListener deleteListener = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            boolean shift = (e.getModifiers () & ActionEvent.CTRL_MASK) != 0;
            deleteSelected (shift);
        }
    };

    ActionListener moveListener = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            moveSelected (Integer.valueOf (e.getActionCommand ()));
        }
    };

    ActionListener runListener = new ActionListener ()
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

    ActionListener exportListener = new ActionListener ()
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
            final JFileChooser fc = new JFileChooser (UMF.getAppResourceDir ());
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

    ActionListener importListener = new ActionListener ()
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
            final JFileChooser fc = new JFileChooser (UMF.getAppResourceDir ());
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
            if (type.equals ("Part")) return;  // For anything but Part, fall through and add it to the newly-created model.
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

        panelSearch.insertDoc (result);
        return result;
    }

    public void editSelected ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path != null) tree.startEditingAtPath (path);

        path = tree.getSelectionPath ();  // This can change quite a bit in the NodeBase.applyEdit() function.
        if (path != null) updateOverrides (path);
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
                    panelSearch.removeDoc (record);
                    ((MDoc) record).delete ();
                    record = null;
                    root = null;
                    model.setRoot (null);
                }
            }
            else
            {
                NodeBase parent = (NodeBase) selected.getParent ();
                int index = parent.getIndex (selected);

                selected.delete (tree);

                index = Math.min (index, parent.getChildCount () - 1);
                TreePath path;
                if (index < 0) path = new TreePath (                          parent.getPath ());
                else           path = new TreePath (((DefaultMutableTreeNode) parent.getChildAt (index)).getPath ());
                tree.setSelectionPath (path);

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
            DefaultMutableTreeNode node   = (DefaultMutableTreeNode) path.getLastPathComponent ();
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent ();
            if (parent instanceof NodePart)  // Only parts support $metadata.gui.order
            {
                int index = parent.getIndex (node) + direction;
                if (index >= 0  &&  index < parent.getChildCount ())
                {
                    model.removeNodeFromParent (node);
                    model.insertNodeInto (node, parent, index);

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

                    NodePart p = (NodePart) parent;
                    MPart metadataPart = null;
                    if (metadataNode == null)
                    {
                        metadataPart = (MPart) p.source.set ("", "$metadata");
                        metadataNode = new NodeAnnotations (metadataPart);
                        model.insertNodeInto (metadataNode, p, 0);
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
                        model.insertNodeInto (orderNode, metadataNode, metadataNode.getChildCount ());
                    }
                    else
                    {
                        orderNode.source.set (order);
                        orderNode.setUserObject ("gui.order=" + order);
                        model.nodeChanged (orderNode);
                    }

                    path = new TreePath (model.getPathToRoot (node));
                    tree.setSelectionPath (path);
                }
            }
        }
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
            DefaultMutableTreeNode node   = (DefaultMutableTreeNode) path.getLastPathComponent ();
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent ();
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
                        a.setUserObject ("gui.order=" + order);
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
        System.out.println ("repaintSouth");
        Rectangle node    = tree.getPathBounds (path);
        Rectangle visible = scrollPane.getViewport ().getViewRect ();
        visible.height -= node.y - visible.y;
        visible.y       = node.y;
        tree.repaint (visible);
        System.out.println ("  called");
    }
}
