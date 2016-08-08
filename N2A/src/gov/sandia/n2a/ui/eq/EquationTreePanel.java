/*
Copyright 2013 Sandia Corporation.
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
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.EventObject;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import replete.gui.controls.IconButton;
import replete.gui.windows.Dialogs;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.threads.CommonThread;
import replete.threads.CommonThreadShutdownException;
import replete.util.ExceptionUtil;
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
    protected JButton btnAddAnnot;
    protected JButton btnAddRef;
    protected JButton btnAddEqn;
    protected JButton btnRemove;
    protected JButton btnMoveUp;
    protected JButton btnMoveDown;
    protected JButton btnRun;
    protected JPopupMenu mnuEqPopup;

    public class NodeEditor extends DefaultTreeCellEditor
    {
        public NodeBase editingNode;

        public NodeEditor (JTree tree, DefaultTreeCellRenderer renderer)
        {
            super (tree, renderer);
        }
        
        @Override
        public boolean isCellEditable (EventObject e)
        {
            if (! super.isCellEditable (e)) return false;
            Object o = lastPath.getLastPathComponent ();
            if (! (o instanceof NodeBase)) return false;
            editingNode = (NodeBase) o;
            return editingNode.allowEdit ();
        }
    }

    public EquationTreePanel (UIController uic, MNode record)
    {
        root  = new NodePart ();
        model = new DefaultTreeModel (root);
        tree  = new JTree (model);
        loadRootFromDB (record);

        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection. It only makes deletes and moves more complicated.
        tree.setEditable (true);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer ()
        {
            @Override
            public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);
                NodeBase n = (NodeBase) value;
                if (n.source.isFromTopDocument ()) setForeground (Color.black);
                else                               setForeground (Color.blue);
                n.prepareRenderer (this, selected, expanded, hasFocus);
                return this;
            }
        };
        tree.setCellRenderer (renderer);

        NodeEditor editor = new NodeEditor (tree, renderer);
        editor.addCellEditorListener (new CellEditorListener ()
        {
            @Override
            public void editingStopped (ChangeEvent e)
            {
                editor.editingNode.applyEdit (model);
            }

            @Override
            public void editingCanceled (ChangeEvent e)
            {
            }
        });
        tree.setCellEditor (editor);

        tree.addMouseListener (new MouseAdapter ()
        {
            public void mouseReleased (MouseEvent e)  // TODO: Since this is a popup menu, would it make sense for this to be mousePressed() instead?
            {
                if (SwingUtilities.isRightMouseButton (e)  &&   e.getClickCount () == 1)
                {
                    TreePath path = tree.getPathForLocation (e.getX (), e.getY ());
                    if (path != null)
                    {
                        tree.setSelectionPath (path);
                        mnuEqPopup.show (tree, e.getX (), e.getY ());
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
                    deleteSelected ();
                }
                else if (keycode == KeyEvent.VK_INSERT)
                {
                    addAtSelected ("");
                }
                else if (keycode == KeyEvent.VK_ENTER)
                {
                    TreePath path = tree.getSelectionPath ();
                    if (path != null) tree.startEditingAtPath (path);
                }
                else if (e.isAltDown ())
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

        // Side Buttons

        btnAddEqn = new IconButton (ImageUtil.getImage ("compnew.gif"), 2);
        btnAddEqn.setToolTipText ("Add Equation");
        btnAddEqn.setActionCommand ("Equation");
        btnAddEqn.addActionListener (addListener);

        btnAddAnnot = new IconButton (ImageUtil.getImage ("addannot.gif"), 2);
        btnAddAnnot.setToolTipText ("Add Annotation");
        btnAddAnnot.setActionCommand ("Annotation");
        btnAddAnnot.addActionListener (addListener);

        btnAddRef = new IconButton (ImageUtil.getImage ("booknew.gif"), 2);
        btnAddRef.setToolTipText ("Add Reference");
        btnAddRef.setActionCommand ("Reference");
        btnAddRef.addActionListener (addListener);

        btnRemove = new IconButton (ImageUtil.getImage ("remove.gif"), 2);
        btnRemove.setToolTipText ("Remove");
        btnRemove.addActionListener (deleteListener);

        btnMoveUp = new IconButton (ImageUtil.getImage ("up.gif"), 2);
        btnMoveUp.setToolTipText ("Move Up");
        btnMoveUp.setActionCommand ("-1");
        btnMoveUp.addActionListener (moveListener);

        btnMoveDown = new IconButton (ImageUtil.getImage ("down.gif"), 2);
        btnMoveDown.setToolTipText ("Move Down");
        btnMoveDown.setActionCommand ("1");
        btnMoveDown.addActionListener (moveListener);

        btnRun = new IconButton (ImageUtil.getImage ("run.gif"), 2);
        btnRun.setToolTipText ("Run");
        btnRun.addActionListener (runListener);

        // Context Menus
        JMenuItem mnuAddEquation = new JMenuItem ("Add Equation", ImageUtil.getImage ("compnew.gif"));
        mnuAddEquation.setActionCommand ("Equation");
        mnuAddEquation.addActionListener (addListener);

        JMenuItem mnuAddAnnot = new JMenuItem ("Add Annotation", ImageUtil.getImage ("addannot.gif"));
        mnuAddAnnot.setActionCommand ("Annotation");
        mnuAddAnnot.addActionListener (addListener);

        JMenuItem mnuAddRef = new JMenuItem ("Add Reference", ImageUtil.getImage ("booknew.gif"));
        mnuAddRef.setActionCommand ("Reference");
        mnuAddRef.addActionListener (addListener);

        mnuEqPopup = new JPopupMenu ();
        mnuEqPopup.add (mnuAddEquation);
        mnuEqPopup.add (mnuAddAnnot);
        mnuEqPopup.add (mnuAddRef);

        Lay.BLtg (this,
            "C", Lay.p (Lay.sp (tree), "eb=5r"),
            "E", Lay.BxL ("Y",
                Lay.BL (btnAddEqn,   "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (btnAddAnnot, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (btnAddRef,   "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (btnRemove,   "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (btnMoveUp,   "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL (btnMoveDown, "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL (btnRun,      "eb=5b,alignx=0.5,maxH=20"),
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
        if (doc != null) record = doc;
        try
        {
            if (record == null)
            {
                root = null;
                model.setRoot (root);
            }
            else
            {
                root = new NodePart (MPart.collate ((MPersistent) record));
                model.setRoot (root);
                root.build (model);
                root.findConnections ();
                tree.expandRow (0);
            }
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
            deleteSelected ();
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
        public void actionPerformed (ActionEvent e)
        {
            // fire off a simulation
            // The code below is adapted from gove.sandia.n2a.ui.model.RunDetailPanel, specifically the old-style single-run.
            // uiController.prepareAndSubmitRunEnsemble() is the way to set up a run ensemble

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

            uiController.getParentRef ().waitOn ();
            final CommonThread t = new CommonThread ()
            {
                @Override
                public void runThread () throws CommonThreadShutdownException
                {
                    Simulation simulation = sim.createSimulation ();
                    try
                    {
                        ExecutionEnv env = ExecutionEnv.factory ();
                        simulation.execute (run, null, env);
                        uiController.openChildWindow ("jobs", null);
                    }
                    catch (Exception e1)
                    {
                        e1.printStackTrace ();
                        Dialogs.showDetails ("An error occurred while submitting the job.", ExceptionUtil.toCompleteString (e1, 4));
                    }
                }
            };
            t.addProgressListener (new ChangeListener ()
            {
                public void stateChanged (ChangeEvent e)
                {
                    if (t.getResult ().isDone ())
                    {
                        uiController.getParentRef ().waitOff ();
                    }
                }
            });
            t.start ();
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
        if (selected == null)
        {
            // TODO: Create new document 
        }
        else
        {
            NodeBase editMe = selected.add (type, this);
            if (editMe != null)
            {
                TreePath path = new TreePath (editMe.getPath ());
                tree.scrollPathToVisible (path);
                tree.setSelectionPath (path);
                tree.startEditingAtPath (path);
            }
        }
    }

    public void deleteSelected ()
    {
        // TODO: Implement node delete
        System.out.println ("Would have removed a tree node, if the code were written.");
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
                        model.insertNodeInto (new NodeAnnotations (metadataPart), p, 0);
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
}
