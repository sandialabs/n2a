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
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.runs.Run;
import gov.sandia.umf.platform.runs.RunOrient;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.EventObject;

import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import replete.gui.controls.IconButton;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Lay;
import replete.util.User;

public class EquationTreePanel extends JPanel
{
    protected UIController uiController;
    protected MNode record;

    // Tree & Its Model
    public JTree            tree;
    public DefaultTreeModel model;
    public NodePart         root;

    // Controls
    protected JButton buttonAddModel;
    protected JButton buttonAddPart;
    protected JButton buttonAddVariable;
    protected JButton buttonAddEquation;
    protected JButton buttonAddAnnotation;
    protected JButton buttonAddReference;
    protected JButton buttonMoveUp;
    protected JButton buttonMoveDown;
    protected JButton buttonRun;
    protected JButton buttonExport;
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
          - Unfortunately, the text still shifts by one pixel between display and edit modes.
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
                    if (at < 0)
                    {
                        if (equals >= 0)
                        {
                            textField.setCaretPosition (text.length ());
                            textField.moveCaretPosition (equals + 1);
                        }
                        // Otherwise use the default, which selects all and places caret at the end.
                    }
                    else
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

            DefaultCellEditor result = new DefaultCellEditor (textField);
            result.setClickCountToStart (1);
            return result;
        }
    }

    // The main constructor. Most of the real work of setting up the UI is here, including some fairly elaborate listeners.
    public EquationTreePanel (UIController uic)
    {
        uiController = uic;

        model = new DefaultTreeModel (null);
        tree  = new JTree (model);

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
                editor.editingNode.applyEdit (tree);
                updateOrder ();
            }

            @Override
            public void editingCanceled (ChangeEvent e)
            {
                Object o = editor.editingNode.getUserObject ();
                if (! (o instanceof String)  ||  ((String) o).isEmpty ()) editor.editingNode.delete (tree);
            }
        });
        tree.setCellEditor (editor);

        tree.addMouseListener (new MouseAdapter ()
        {
            public void mouseReleased (MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton (e)  &&   e.getClickCount () == 1)
                {
                    TreePath path = tree.getPathForLocation (e.getX (), e.getY ());
                    if (path != null)
                    {
                        tree.setSelectionPath (path);
                        menuPopup.show (tree, e.getX (), e.getY ());
                    }
                }
            }

            public void mouseClicked (MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton (e)  &&  e.getClickCount () == 2)
                {
                    TreePath path = tree.getClosestPathForLocation (e.getX (), e.getY ());
                    if (path != null) tree.startEditingAtPath (path);
                }
            }
        });

        tree.addKeyListener (new KeyAdapter ()
        {
            @Override
            public void keyPressed (KeyEvent e)
            {
                int keycode = e.getKeyCode ();
                if (keycode == KeyEvent.VK_DELETE)
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

        menuPopup = new JPopupMenu ();
        menuPopup.add (menuAddPart);
        menuPopup.add (menuAddVariable);
        menuPopup.add (menuAddEquation);
        menuPopup.add (menuAddAnnotation);
        menuPopup.add (menuAddReference);

        Lay.BLtg (this,
            "C", Lay.p (Lay.sp (tree)),
            "E", Lay.BxL ("Y",
                Lay.BL (buttonAddModel,      "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddPart,       "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddVariable,   "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddEquation,   "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddAnnotation, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonAddReference,  "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonMoveUp,        "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonMoveDown,      "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (buttonRun,           "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (buttonExport,        "eb=5b,alignx=0.5,maxH=20"),
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
            root = new NodePart (MPart.collate ((MPersistent) record));
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

            Simulator simulator = null;
            Simulator internal = null;
            String simulatorName = record.get ("$metadata", "backend");
            for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Simulator.class))
            {
                Simulator s = (Simulator) ext;
                if (s.getName ().equalsIgnoreCase (simulatorName))
                {
                    simulator = s;
                    break;
                }
                if (s.getName ().equals ("Internal")) internal = s;
            }
            if (simulator == null) simulator = internal;
            if (simulator == null)
            {
                System.err.println ("Couldn't find internal simulator");
                return;
            }

            final Simulator sim = simulator;
            final Run run = new RunOrient (0.0, "", null, sim, User.getName (), "Pending", null, record);  // Most of these are useless properties, now handled by backend reading metadata from model.

            new Thread ()
            {
                public void run ()
                {
                    Simulation simulation = sim.createSimulation ();
                    try
                    {
                        ExecutionEnv env = ExecutionEnv.factory ();
                        simulation.execute (run, null, env);
                        uiController.selectTab ("Runs");
                    }
                    catch (Exception e)
                    {
                        // TODO: Instead of throwing an exception, simulation should record all errors/warnings in a file in the job dir.
                        e.printStackTrace ();
                    }
                }
            }.start ();
        }
    };

    ActionListener exportListener = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            uiController.openExportDialog (record);
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
        MNode models = AppData.getInstance ().models;
        String newModelName = "New Model";
        MNode newModel = models.child (newModelName);
        if (newModel == null)
        {
            newModel = models.set ("", newModelName);
        }
        else
        {
            newModelName += " ";
            int suffix = 2;
            while (true)
            {
                if (newModel.length () == 0) break;  // no children, so still a virgin
                newModel = models.child (newModelName + suffix);
                if (newModel == null)
                {
                    newModel = models.set ("", newModelName + suffix);
                    break;
                }
                suffix++;
            }
        }
        loadRootFromDB (newModel);
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
                if (controlKeyDown) selected.delete (tree);  // Only delete the root (entire document) if the user does something extra to say they really mean it.
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
}
