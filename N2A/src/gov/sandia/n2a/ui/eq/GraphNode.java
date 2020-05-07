/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.MouseInputAdapter;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.CompoundEditView;
import gov.sandia.n2a.ui.eq.undo.DeletePart;
import sun.swing.SwingUtilities2;

@SuppressWarnings("serial")
public class GraphNode extends JPanel
{
    protected PanelEquations      container;
    protected GraphPanel          parent;
    public    NodePart            node;
    protected TitleRenderer       title;
    public    boolean             open;
    protected boolean             titleFocused        = true;  // sans any other knowledge, title should be selected first
    protected boolean             selected;
    protected JPanel              panelTitle;
    protected Component           hr                  = Box.createVerticalStrut (border.t + 1);
    public    PanelEquationTree   panelEquationTree;
    protected ResizeListener      resizeListener      = new ResizeListener ();
    protected List<GraphEdge>     edgesOut            = new ArrayList<GraphEdge> ();
    protected List<GraphEdge>     edgesIn             = new ArrayList<GraphEdge> ();

    protected static RoundedBorder border = new RoundedBorder (5);

    public GraphNode (GraphPanel parent, NodePart node)
    {
        container   = PanelModel.instance.panelEquations;  // "container" is merely a convenient shortcut
        this.parent = parent;
        this.node   = node;
        node.graph  = this;

        node.fakeRoot (true);
        if (container.view == PanelEquations.NODE)
        {
            panelEquationTree = new PanelEquationTree (container);
            panelEquationTree.loadPart (node);
        }

        // Internally, this class uses the null/non-null state of panelEquationsTree to indicated whether
        // container.view is NODE or a property panel mode.
        open =  panelEquationTree != null  &&  node.source.getBoolean ("$metadata", "gui", "bounds", "open");

        title = new TitleRenderer ();
        title.getTreeCellRendererComponent (getEquationTree ().tree, node, false, open, false, -1, false);  // Configure JLabel with info from node.
        title.setFocusable (true);            // make focusable in general
        title.setRequestFocusEnabled (true);  // make focusable by mouse

        panelTitle = Lay.BL ("N", title);
        panelTitle.setOpaque (false);
        if (open) panelTitle.add (hr, BorderLayout.CENTER);
        Lay.BLtg (this, "N", panelTitle);
        if (open) add (panelEquationTree, BorderLayout.CENTER);
        setBorder (border);
        setOpaque (false);

        MNode bounds = node.source.child ("$metadata", "gui", "bounds");
        if (bounds != null)
        {
            int x = bounds.getInt ("x") + parent.offset.x;
            int y = bounds.getInt ("y") + parent.offset.y;
            setLocation (x, y);
        }

        addMouseListener (resizeListener);
        addMouseMotionListener (resizeListener);

        InputMap inputMap = getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("ESCAPE"), "cancel");

        ActionMap actionMap = getActionMap ();
        actionMap.put ("cancel", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (title.editingComponent != null) container.editor.cancelCellEditing ();
            }
        });
    }

    public void updateUI ()
    {
        super.updateUI ();

        // This function is probably called by SwingUtilities.updateComponentTreeUI().
        // If we are open, then our equation tree will be automatically included in the walk.
        // If we are closed, then our equation tree will be missed.
        if (open) return;
        if (hr                != null) SwingUtilities.updateComponentTreeUI (hr);
        if (panelEquationTree != null) SwingUtilities.updateComponentTreeUI (panelEquationTree);
    }

    public PanelEquationTree getEquationTree ()
    {
        if (panelEquationTree == null) return container.panelEquationTree;
        return panelEquationTree;
    }

    public Component getTargetComponent ()
    {
        if (titleFocused) return title;
        return getEquationTree ().tree;
    }

    public boolean titleIsFocusOwner ()
    {
        return title.isFocusOwner ();
    }

    public void takeFocusOnTitle ()
    {
        titleFocused = true;
        takeFocus ();
    }

    public void takeFocus ()
    {
        // A call to title.requestFocusInWindow() may not result in a call to focusGained()
        // soon enough to get this graph node ready for edit playback. Thus we need to directly call
        // restoreFocus every time. This will result in some redundant calls to restoreFocus(),
        // but this is safer than relying on Swing's scheduling.
        restoreFocus ();
        if (titleFocused) title.requestFocusInWindow ();
        else              getEquationTree ().takeFocus ();
    }

    /**
        Subroutine of takeFocus(). Called either directly by takeFocus() or indirectly by title focus listener.
    **/
    public void restoreFocus ()
    {
        container.setSelected (false);
        if (panelEquationTree == null)
        {
            container.active = container.panelEquationTree;
            if (container.panelEquationTree.root != node  &&  ! node.toString ().isEmpty ())  // Only load tree if node is not blank. Usually, a blank node is about to be deleted.
            {
                container.panelEquationTree.loadPart (node);
                FocusCacheEntry fce = container.createFocus (node);
                if (fce.sp != null) fce.sp.restore (container.panelEquationTree.tree, false);
            }
        }
        else
        {
            container.active = panelEquationTree;
        }

        parent.setComponentZOrder (this, 0);
        parent.scrollRectToVisible (getBounds ());
        repaint ();

        // Since parent node is always on top, we must shift the graph to avoid occlusion.
        if (container.panelParent.isVisible ())
        {
            Point     me = getLocation ();
            Dimension d  = container.panelParent.getSize ();
            Point     p  = container.panelEquationGraph.vp.getViewPosition ();
            int ox = d.width  - me.x + p.x;
            int oy = d.height - me.y + p.y;
            if (ox > 0  &&  oy > 0)
            {
                if (ox < oy) p.x -= ox;
                else         p.y -= oy;
                parent.layout.shiftViewport (p);
                parent.revalidate ();
                parent.repaint ();
            }
        }
    }

    public void switchFocus (boolean ontoTitle, boolean selectRow0)
    {
        PanelEquationTree pet = getEquationTree ();
        if (pet.tree.getRowCount () == 0) ontoTitle = true;  // Don't focus tree if is empty.

        titleFocused = ontoTitle;
        if (ontoTitle)
        {
            title.requestFocusInWindow ();  // Triggers restoreFocus() via title focus listener.
        }
        else
        {
            if (panelEquationTree == null) pet.loadPart (node);  // Because switchFocus() can also be used to grab focus from another part.
            else                           setOpen (true);
            if (selectRow0)
            {
                pet.tree.scrollRowToVisible (0);
                pet.tree.setSelectionRow (0);
            }
            pet.takeFocus ();
        }
    }

    public void toggleOpen ()
    {
        boolean nextOpen = ! open;
        setOpen (nextOpen);
        if (! container.locked) node.source.set (nextOpen, "$metadata", "gui", "bounds", "open");
    }

    public void setOpen (boolean value)
    {
        if (open == value) return;
        open = value;
        if (open)
        {
            panelTitle.add (hr, BorderLayout.CENTER);
            add (panelEquationTree, BorderLayout.CENTER);
        }
        else
        {
            titleFocused = true;
            if (panelEquationTree.tree.isFocusOwner ()) title.requestFocusInWindow ();

            panelTitle.remove (hr);
            remove (panelEquationTree);  // assume that equation tree does not have focus
        }
        boolean focused = title.isFocusOwner ();
        title.getTreeCellRendererComponent (panelEquationTree.tree, node, focused || selected, open, false, -1, focused);
        animate (new Rectangle (getLocation (), getPreferredSize ()));
    }

    public void setSelected (boolean value)
    {
        if (selected == value) return;
        selected = value;
        title.updateSelected ();
    }

    public Dimension getPreferredSize ()
    {
        int w = 0;
        int h = 0;
        MNode bounds = node.source.child ("$metadata", "gui", "bounds");
        if (bounds != null)
        {
            if (open)
            {
                MNode boundsOpen = bounds.child ("open");
                if (boundsOpen != null)
                {
                    w = boundsOpen.getInt ("width");
                    h = boundsOpen.getInt ("height");
                }
            }
            else  // closed
            {
                w = bounds.getInt ("width");
                h = bounds.getInt ("height");
            }
        }
        if (w != 0  &&  h != 0) return new Dimension (w, h);

        Dimension d = super.getPreferredSize ();  // Gets the layout manager's opinion.
        d.width  = Math.max (d.width,  w);
        d.height = Math.max (d.height, h);

        // Don't exceed current size of viewport.
        // Should this limit be imposed on user settings as well?
        Dimension extent = ((JViewport) parent.getParent ()).getExtentSize ();
        d.width  = Math.min (d.width,  extent.width);
        d.height = Math.min (d.height, extent.height);

        return d;
    }

    public void nudge (ActionEvent e, int dx, int dy)
    {
        if (container.locked) return;

        int step = 1;
        if ((e.getModifiers () & ActionEvent.CTRL_MASK) != 0) step = 10;

        MNode metadata = new MVolatile ();
        if (dx != 0)
        {
            int x = getBounds ().x - parent.offset.x + dx * step;
            metadata.set (x, "gui", "bounds", "x");
        }
        if (dy != 0)
        {
            int y = getBounds ().y - parent.offset.y + dy * step;
            metadata.set (y, "gui", "bounds", "y");
        }
        MainFrame.instance.undoManager.apply (new ChangeAnnotations (node, metadata));
    }

    public void updateTitle ()
    {
        Rectangle old = getBounds ();

        node.setUserObject ();
        boolean focused = title.isFocusOwner ();
        title.getTreeCellRendererComponent (getEquationTree ().tree, node, focused || selected, open, false, -1, focused);

        panelTitle.invalidate ();
        setSize (getPreferredSize ());  // GraphLayout won't do this, so we must do it manually.
        Rectangle next = getBounds ();
        parent.layout.componentMoved (this);
        parent.repaint (old.union (next));
    }

    /**
        Apply any changes from $metadata.
    **/
    public void updateGUI ()
    {
        int x = parent.offset.x;
        int y = parent.offset.y;
        MNode bounds = node.source.child ("$metadata", "gui", "bounds");
        if (bounds != null)
        {
            x += bounds.getInt ("x");
            y += bounds.getInt ("y");
            if (panelEquationTree != null) setOpen (bounds.getBoolean ("open"));
        }
        Dimension d = getPreferredSize ();  // Fetches updated width and height.
        Rectangle r = new Rectangle (x, y, d.width, d.height);
        animate (r);
        parent.scrollRectToVisible (r);
    }

    public void killEdge (String alias)
    {
        updateEdge (alias, null, true);
    }

    public void updateEdge (String alias, NodePart partTo)
    {
        updateEdge (alias, partTo, false);
    }

    /**
        Apply changes from a connection binding.
    **/
    public void updateEdge (String alias, NodePart partTo, boolean kill)
    {
        GraphEdge edge = null;
        for (GraphEdge ge : edgesOut)
        {
            if (ge.alias.equals (alias))
            {
                edge = ge;
                break;
            }
        }
        if (kill  &&  edge == null) return;  // nothing to do

        Rectangle paintRegion = new Rectangle (0, 0, -1, -1);
        if (edge != null)  // Remove old edge
        {
            paintRegion = edge.bounds;
            parent.edges.remove (edge);
            edgesOut.remove (edge);
            if (edge.nodeTo != null) edge.nodeTo.edgesIn.remove (edge);
        }
        if (! kill)  // Recreate edge
        {
            edge = new GraphEdge (this, partTo, alias);
            parent.edges.add (edge);
            edgesOut.add (edge);
            if (edge.nodeTo != null) edge.nodeTo.edgesIn.add (edge);
        }

        // Adjust binary connections
        if (edgesOut.size () == 2)
        {
            GraphEdge A = edgesOut.get (0);
            GraphEdge B = edgesOut.get (1);
            A.edgeOther = B;
            B.edgeOther = A;
        }
        else
        {
            for (GraphEdge ge : edgesOut) ge.edgeOther = null;
        }

        // Repaint all current edges
        Point offsetBefore = new Point (parent.offset);
        for (GraphEdge ge : edgesOut)
        {
            paintRegion = paintRegion.union (ge.bounds);
            ge.updateShape (false);
            paintRegion = paintRegion.union (ge.bounds);
            parent.layout.componentMoved (ge.bounds);  // This can shift all components, so need to shift paintRegion accordingly.
        }
        parent.scrollRectToVisible (edge.bounds);
        paintRegion.x += parent.offset.x - offsetBefore.x;
        paintRegion.y += parent.offset.y - offsetBefore.y;
        parent.repaint (paintRegion);
    }

    /**
        Sets bounds to current preferred size and updates everything affected by the change.
    **/
    public void animate ()
    {
        Rectangle next = new Rectangle (getLocation (), getPreferredSize ());
        if (getBounds () != next) animate (next);
    }

    /**
        Sets bounds and repaints portion of container that was exposed by move or resize.
    **/
    public void animate (Rectangle next)
    {
        Rectangle paintRegion = next.union (getBounds ());
        setBounds (next);
        parent.layout.componentMoved (next);

        for (GraphEdge ge : edgesOut)
        {
            paintRegion = paintRegion.union (ge.bounds);
            ge.updateShape (false);
            paintRegion = paintRegion.union (ge.bounds);
            parent.layout.componentMoved (ge.bounds);
        }
        for (GraphEdge ge : edgesIn)
        {
            paintRegion = paintRegion.union (ge.bounds);
            if (ge.edgeOther != null) paintRegion = paintRegion.union (ge.edgeOther.bounds);
            ge.updateShape (true);
            paintRegion = paintRegion.union (ge.bounds);
            parent.layout.componentMoved (ge.bounds);
            if (ge.edgeOther != null)
            {
                paintRegion = paintRegion.union (ge.edgeOther.bounds);
                parent.layout.componentMoved (ge.edgeOther.bounds);
            }
        }
        validate ();  // Preemptively redo internal layout, so this component will repaint correctly.
        parent.repaint (paintRegion);
    }

    public class TitleRenderer extends EquationTreeCellRenderer implements CellEditorListener
    {
        protected Component editingComponent;
        protected boolean   UIupdated;

        public TitleRenderer ()
        {
            nontree = true;

            setTransferHandler (container.transferHandler);

            InputMap inputMap = getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("UP"),               "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("DOWN"),             "selectNext");
            inputMap.put (KeyStroke.getKeyStroke ("LEFT"),             "close");
            inputMap.put (KeyStroke.getKeyStroke ("RIGHT"),            "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl UP"),          "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl DOWN"),        "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl LEFT"),        "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl RIGHT"),       "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("shift UP"),         "moveUp");
            inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"),       "moveDown");
            inputMap.put (KeyStroke.getKeyStroke ("shift LEFT"),       "moveLeft");
            inputMap.put (KeyStroke.getKeyStroke ("shift RIGHT"),      "moveRight");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl UP"),    "moveUp");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl DOWN"),  "moveDown");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl LEFT"),  "moveLeft");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl RIGHT"), "moveRight");
            inputMap.put (KeyStroke.getKeyStroke ("shift DELETE"),     "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl X"),           "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl INSERT"),      "copy");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl C"),           "copy");
            inputMap.put (KeyStroke.getKeyStroke ("shift INSERT"),     "paste");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl V"),           "paste");
            inputMap.put (KeyStroke.getKeyStroke ("INSERT"),           "add");
            inputMap.put (KeyStroke.getKeyStroke ("DELETE"),           "delete");
            inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),       "delete");
            inputMap.put (KeyStroke.getKeyStroke ("ENTER"),            "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("F2"),               "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl D"),     "drillUp");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl D"),           "drillDown");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl O"),           "outsource");
            inputMap.put (KeyStroke.getKeyStroke ("SPACE"),            "toggleSelection");

            ActionMap actionMap = getActionMap ();
            actionMap.put ("close", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (panelEquationTree != null  &&  open) toggleOpen ();
                }
            });
            actionMap.put ("selectNext", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (panelEquationTree != null  &&  ! open) toggleOpen ();  // because switchFocus() does not change metadata "open" flag
                    container.panelEquationGraph.clearSelection ();
                    switchFocus (false, panelEquationTree != null);
                }
            });
            actionMap.put ("selectChild", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (panelEquationTree != null  &&  ! open)
                    {
                        toggleOpen ();
                    }
                    else
                    {
                        container.panelEquationGraph.clearSelection ();
                        switchFocus (false, false);
                    }
                }
            });
            actionMap.put ("moveUp", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    nudge (e, 0, -1);  // guards against modifying a locked part
                }
            });
            actionMap.put ("moveDown", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    nudge (e, 0, 1);
                }
            });
            actionMap.put ("moveLeft", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    nudge (e, -1, 0);
                }
            });
            actionMap.put ("moveRight", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    nudge (e, 1, 0);
                }
            });
            actionMap.put ("cut",   TransferHandler.getCutAction ());
            actionMap.put ("copy",  TransferHandler.getCopyAction ());
            actionMap.put ("paste", TransferHandler.getPasteAction ());
            actionMap.put ("add", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (open)
                    {
                        panelEquationTree.addAtSelected ("");  // No selection should be active, so this should default to root (same as our "node").
                    }
                    else  // closed, so add new peer part in graph area near this node
                    {
                        Point location = GraphNode.this.getLocation ();
                        location.x += 100 - parent.offset.x;
                        location.y += 100 - parent.offset.y;
                        NodePart editMe = (NodePart) container.part.add ("Part", null, null, location);
                        if (editMe == null) return;
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                editMe.graph.title.startEditing ();
                            }
                        });
                    }
                }
            });
            actionMap.put ("delete", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    // Compare this implementation with NodePart.delete(). Here we specifically handle graph nodes.

                    if (! node.source.isFromTopDocument ()) return;

                    List<GraphNode> selected = parent.getSelection ();
                    selected.remove (GraphNode.this);

                    UndoManager um = MainFrame.instance.undoManager;
                    if (selected.isEmpty ())
                    {
                        um.apply (new DeletePart (node, false));
                    }
                    else
                    {
                        selected.add (GraphNode.this);  // Now at end of list, which is where we want it.
                        CompoundEditView compound = new CompoundEditView (CompoundEditView.CLEAR_GRAPH);
                        compound.leadPath = node.getKeyPath ();
                        // No need to clear selection, because all nodes are going away.
                        um.addEdit (compound);
                        int last = selected.size () - 1;
                        int i = 0;
                        for (GraphNode g : selected)
                        {
                            DeletePart d = new DeletePart (g.node, false);
                            d.setMulti (true);
                            if (i++ == last) d.setMultiLast (true);
                            um.apply (d);
                        }
                        um.endCompoundEdit ();
                        // No need to focus the lead, because all nodes are gone. Instead, rely on behavior of multiLast to focus another node.
                    }
                }
            });
            actionMap.put ("startEditing", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    startEditing ();  // guards against modifying a locked part
                }
            });
            actionMap.put ("drillUp", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    container.drillUp ();
                }
            });
            actionMap.put ("drillDown", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    container.drill (node);
                }
            });
            actionMap.put ("outsource", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    PanelEquationTree.outsource (node);
                }
            });
            actionMap.put ("toggleSelection", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    GraphNode.this.selected = ! GraphNode.this.selected;
                }
            });

            MouseInputAdapter mouseListener = new MouseInputAdapter ()
            {
                public void translate (MouseEvent me)
                {
                    Insets i = GraphNode.this.getInsets ();  // Due to layout, this should be the only adjustment we need.
                    me.translatePoint (i.left, i.top);
                    me.setSource (GraphNode.this);
                }

                public void mouseClicked (MouseEvent me)
                {
                    int x = me.getX ();
                    int y = me.getY ();
                    int clicks = me.getClickCount ();
                    boolean select =  me.isControlDown ()  ||  me.isShiftDown ();

                    if (SwingUtilities.isLeftMouseButton (me))
                    {
                        if (clicks == 1)  // Open/close
                        {
                            int iconWidth = node.getIcon (open).getIconWidth ();  // "open" isn't actually important for root node, as NodePart doesn't currently change appearance.
                            if (x < iconWidth)
                            {
                                if (panelEquationTree != null) toggleOpen ();
                            }
                            else if (isFocusOwner ())
                            {
                                startEditing ();
                                return;
                            }
                            if (select)
                            {
                                GraphNode.this.selected = true;
                                // We are not the focus owner (implied by above code), so we should ensure that
                                // the current focus owner is selected. A simple way is to assume that the "active" tree is up-to-date.
                                GraphNode g = PanelModel.getGraphNode (KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ());
                                if (g != null) g.setSelected (true);
                            }
                            else
                            {
                                container.panelEquationGraph.clearSelection ();
                            }
                            switchFocus (true, false);
                        }
                        else if (clicks == 2)  // Drill down
                        {
                            container.drill (node);
                        }
                    }
                    else if (SwingUtilities.isRightMouseButton (me))
                    {
                        if (clicks == 1)  // Show popup menu
                        {
                            container.panelEquationGraph.clearSelection ();
                            switchFocus (true, false);
                            container.menuPopup.show (title, x, y);
                        }
                    }
                }

                public void mouseMoved (MouseEvent me)
                {
                    int x = me.getX ();
                    int iconWidth = node.getIcon (open).getIconWidth ();
                    if (x < iconWidth)
                    {
                        setCursor (Cursor.getPredefinedCursor (Cursor.DEFAULT_CURSOR));
                    }
                    else if (! container.locked  &&  resizeListener.start == null)
                    {
                        setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    }
                }

                public void mouseExited (MouseEvent me)
                {
                    translate (me);
                    resizeListener.mouseExited (me);
                }

                public void mousePressed (MouseEvent me)
                {
                    translate (me);
                    resizeListener.mousePressed (me);
                }

                public void mouseDragged (MouseEvent me)
                {
                    translate (me);
                    resizeListener.mouseDragged (me);
                }

                public void mouseReleased (MouseEvent me)
                {
                    titleFocused = true;  // When resizeListener processes this event, it will call takeFocus(). The focus should always go to title when title was clicked.
                    translate (me);
                    resizeListener.mouseReleased (me);
                }
            };
            addMouseListener (mouseListener);
            addMouseMotionListener (mouseListener);

            addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                    getTreeCellRendererComponent (getEquationTree ().tree, node, true, open, false, -1, true);
                    restoreFocus ();  // does repaint
                }

                public void focusLost (FocusEvent e)
                {
                    Component other = e.getOppositeComponent ();
                    if (other != null)  // Focus remains inside application.
                    {
                        GraphNode g = PanelModel.getGraphNode (other);
                        if (g == null) container.panelEquationGraph.clearSelection ();  // Next focus in not a graph node, so unset all selections. This avoids visual confusion.
                    }

                    getTreeCellRendererComponent (getEquationTree ().tree, node, GraphNode.this.selected, open, false, -1, false);
                    GraphNode.this.repaint ();
                }
            });
        }

        public void updateUI ()
        {
            super.updateUI ();
            UIupdated = true;
        }

        public Dimension getPreferredSize ()
        {
            if (UIupdated)
            {
                UIupdated = false;
                // We are never the focus owner, because updateUI() is triggered from the L&F panel.
                getTreeCellRendererComponent (getEquationTree ().tree, node, false, open, false, -1, false);
            }
            return super.getPreferredSize ();
        }

        public void updateSelected ()
        {
            boolean focused = isFocusOwner ();
            getTreeCellRendererComponent (getEquationTree ().tree, node, GraphNode.this.selected || focused, open, false, -1, focused);
            GraphNode.this.repaint ();
        }

        /**
            Follows example of openjdk javax.swing.plaf.basic.BasicTreeUI.startEditing()
        **/
        public void startEditing ()
        {
            if (container.locked) return;

            if (container.editor.editingNode != null) container.editor.stopCellEditing ();  // Edit could be in progress on another node title or on any tree, including our own.
            container.editor.addCellEditorListener (this);
            editingComponent = container.editor.getTitleEditorComponent (getEquationTree ().tree, node, open);
            panelTitle.add (editingComponent, BorderLayout.NORTH, 0);  // displaces this renderer from the layout manager's north slot
            setVisible (false);  // hide this renderer

            GraphNode.this.setSize (GraphNode.this.getPreferredSize ());
            GraphNode.this.validate ();
            parent.scrollRectToVisible (GraphNode.this.getBounds ());
            GraphNode.this.repaint ();
            SwingUtilities2.compositeRequestFocus (editingComponent);  // editingComponent is really a container, so we shift focus to the first focusable child of editingComponent
        }

        public void completeEditing (boolean canceled)
        {
            container.editor.removeCellEditorListener (this);
            if (! canceled) node.setUserObject (container.editor.getCellEditorValue ());

            setVisible (true);
            panelTitle.getLayout ().addLayoutComponent (BorderLayout.NORTH, this);  // restore this renderer to the layout manger's north slot
            panelTitle.remove (editingComponent);  // triggers shift of focus back to this renderer
            editingComponent = null;
        }

        public void editingStopped (ChangeEvent e)
        {
            completeEditing (false);
        }

        public void editingCanceled (ChangeEvent e)
        {
            completeEditing (true);
        }
    }

    public class ResizeListener extends MouseInputAdapter implements ActionListener
    {
        int             cursor;
        Point           start;
        Dimension       min;
        Rectangle       old;
        boolean         connect;
        GraphEdge       edge;  // Paints edge when in dragging in connect mode.
        MouseEvent      lastEvent;
        Timer           timer = new Timer (100, this);
        List<GraphNode> selection;

        public void mouseClicked (MouseEvent me)
        {
            if (SwingUtilities.isLeftMouseButton (me))
            {
                if (me.getClickCount () == 2)
                {
                    // Drill down
                    PanelEquations pe = PanelModel.instance.panelEquations;
                    pe.saveFocus ();
                    FocusCacheEntry fce = pe.createFocus (pe.part);
                    fce.subpart = node.source.key ();
                    pe.loadPart (node);
                }
            }
        }

        public void mouseMoved (MouseEvent me)
        {
            if (container.locked) return;
            if (start == null) setCursor (Cursor.getPredefinedCursor (border.getCursor (me)));
        }

        public void mouseExited (MouseEvent me)
        {
            // It is possible to get this event in the middle of a drag, so ignore that case.
            if (start == null) setCursor (Cursor.getDefaultCursor ());
        }

        public void mousePressed (MouseEvent me)
        {
            if (container.locked) return;
            if (! SwingUtilities.isLeftMouseButton (me)) return;

            // All mouse event coordinates are relative to the bounds of this component.
            parent.setComponentZOrder (GraphNode.this, 0);
            start   = me.getPoint ();
            min     = getMinimumSize ();
            old     = getBounds ();
            connect = me.isShiftDown ();
            edge    = null;
            cursor  = border.getCursor (me);
            setCursor (Cursor.getPredefinedCursor (cursor));

            selection = container.panelEquationGraph.getSelection ();
            selection.remove (GraphNode.this);
        }

        public void mouseDragged (MouseEvent me)
        {
            if (start == null) return;

            JViewport vp = (JViewport) parent.getParent ();
            Point pp = vp.getLocationOnScreen ();
            Point pm = me.getLocationOnScreen ();
            pm.x -= pp.x;
            pm.y -= pp.y;
            Dimension extent = vp.getExtentSize ();
            boolean auto =  me == lastEvent;
            if (pm.x < 0  ||  pm.x > extent.width  ||  pm.y < 0  ||  pm.y > extent.height)
            {
                if (auto)
                {
                    int dx = pm.x < 0 ? pm.x : (pm.x > extent.width  ? pm.x - extent.width  : 0);
                    int dy = pm.y < 0 ? pm.y : (pm.y > extent.height ? pm.y - extent.height : 0);

                    Point p = vp.getViewPosition ();
                    p.translate (dx, dy);
                    parent.layout.shiftViewport (p);
                    me.translatePoint (dx, dy);  // Makes permanent change to lastEvent. Does not change its getLocationOnScreen()
                }
                else  // A regular drag
                {
                    lastEvent = me;  // Let the user adjust speed.
                    timer.start ();
                    return;  // Don't otherwise process it.
                }
            }
            else
            {
                timer.stop ();
                lastEvent = null;
                if (auto) return;
            }

            int x = getX ();
            int y = getY ();

            if (connect)
            {
                if (edge == null)
                {
                    // Create and install edge
                    edge = new GraphEdge (GraphNode.this, null, "");
                    edge.anchor = new Point (start);  // Position in this component where drag started.
                    edge.tip = new Vector2 (0, 0);  // This is normally created by GraphEdge.updateShape(), but we don't call that first.
                    parent.edges.add (edge);
                }
                int nx = Math.max (x + me.getX (), 0);
                int ny = Math.max (y + me.getY (), 0);
                edge.animate (new Point (nx, ny));
                return;
            }

            int w = getWidth ();
            int h = getHeight ();
            int dx = me.getX () - start.x;
            int dy = me.getY () - start.y;
            if (x + dx < 0) dx = -x;  // x is never less than 0
            if (y + dy < 0) dy = -y;  // ditto

            switch (cursor)
            {
                case Cursor.NW_RESIZE_CURSOR:
                    int newW = w - dx;
                    if (newW < min.width)
                    {
                        dx -= min.width - newW;
                        newW = min.width;
                    }
                    int newH = h - dy;
                    if (newH < min.height)
                    {
                        dy -= min.height - newH;
                        newH = min.height;
                    }
                    animate (new Rectangle (x + dx, y + dy, newW, newH));
                    if (auto) me.translatePoint (-dx, -dy);  // Ensure that me continues to be relative to current position of this component.
                    break;
                case Cursor.N_RESIZE_CURSOR:
                    newH = h - dy;
                    if (newH < min.height)
                    {
                        dy -= min.height - newH;
                        newH = min.height;
                    }
                    animate (new Rectangle (x, y + dy, w, newH));
                    if (auto) me.translatePoint (0, -dy);
                    break;
                case Cursor.NE_RESIZE_CURSOR:
                    newW = w + dx;
                    if (newW < min.width)
                    {
                        dx += min.width - newW;
                        newW = min.width;
                    }
                    newH = h - dy;
                    if (newH < min.height)
                    {
                        dy -= min.height - newH;
                        newH = min.height;
                    }
                    animate (new Rectangle (x, y + dy, newW, newH));
                    if (auto) me.translatePoint (0, -dy);
                    start.translate (dx, 0);
                    break;
                case Cursor.E_RESIZE_CURSOR:
                    newW = w + dx;
                    if (newW < min.width)
                    {
                        dx += min.width - newW;
                        newW = min.width;
                    }
                    animate (new Rectangle (x, y, newW, h));
                    start.translate (dx, 0);
                    break;
                case Cursor.SE_RESIZE_CURSOR:
                    newW = w + dx;
                    if (newW < min.width)
                    {
                        dx += min.width - newW;
                        newW = min.width;
                    }
                    newH = h + dy;
                    if (newH < min.height)
                    {
                        dy += min.height - newH;
                        newH = min.height;
                    }
                    animate (new Rectangle (x, y, newW, newH));
                    start.translate (dx, dy);
                    break;
                case Cursor.S_RESIZE_CURSOR:
                    newH = h + dy;
                    if (newH < min.height)
                    {
                        dy += min.height - newH;
                        newH = min.height;
                    }
                    animate (new Rectangle (x, y, w, newH));
                    start.translate (0, dy);
                    break;
                case Cursor.SW_RESIZE_CURSOR:
                    newW = w - dx;
                    if (newW < min.width)
                    {
                        dx -= min.width - newW;
                        newW = min.width;
                    }
                    newH = h + dy;
                    if (newH < min.height)
                    {
                        dy += min.height - newH;
                        newH = min.height;
                    }
                    animate (new Rectangle (x + dx, y, newW, newH));
                    if (auto) me.translatePoint (-dx, 0);
                    start.translate (0, dy);
                    break;
                case Cursor.W_RESIZE_CURSOR:
                    newW = w - dx;
                    if (newW < min.width)
                    {
                        dx -= min.width - newW;
                        newW = min.width;
                    }
                    animate (new Rectangle (x + dx, y, newW, h));
                    if (auto) me.translatePoint (-dx, 0);
                    break;
                case Cursor.MOVE_CURSOR:
                    animate (new Rectangle (x + dx, y + dy, w, h));
                    for (GraphNode g : selection)
                    {
                        Rectangle bounds = g.getBounds ();
                        bounds.setLocation (bounds.x + dx, bounds.y + dy);
                        g.animate (bounds);
                    }
                    if (auto) me.translatePoint (-dx, -dy);
            }
        }

        public void mouseReleased (MouseEvent me)
        {
            start = null;
            timer.stop ();

            if (SwingUtilities.isLeftMouseButton (me))
            {
                if (connect)
                {
                    if (edge != null)
                    {
                        parent.edges.remove (edge);
                        parent.repaint (edge.bounds);

                        GraphNode gn = parent.findNodeAt (new Point (getX () + me.getX (), getY () + me.getY ()));
                        if (gn != null)
                        {
                            List<NodePart> query = new ArrayList<NodePart> ();
                            query.add (node);
                            query.add (gn.node);
                            PanelModel.instance.panelSearch.search (query);
                        }

                        edge = null;
                        GraphNode.this.selected = false;  // Don't let clearSelection() trigger an update to our renderer.
                        container.panelEquationGraph.clearSelection ();
                        // takeFocus() is called below
                    }
                }
                else if (cursor != Cursor.DEFAULT_CURSOR)
                {
                    // Store new bounds in metadata
                    MNode metadata = new MVolatile ();
                    MNode bounds = metadata.childOrCreate ("gui", "bounds");
                    Rectangle now = getBounds ();
                    int dx = now.x - old.x;
                    int dy = now.y - old.y;
                    boolean moved =  dx != 0  ||  dy != 0;
                    if (dx != 0) bounds.set (now.x - parent.offset.x, "x");
                    if (dy != 0) bounds.set (now.y - parent.offset.y, "y");
                    if (open)
                    {
                        MNode boundsOpen = bounds.childOrCreate ("open");
                        if (now.width  != old.width ) boundsOpen.set (now.width,  "width");
                        if (now.height != old.height) boundsOpen.set (now.height, "height");
                        if (boundsOpen.size () == 0) bounds.clear ("open");
                    }
                    else
                    {
                        if (now.width  != old.width ) bounds.set (now.width,  "width");
                        if (now.height != old.height) bounds.set (now.height, "height");
                    }
                    if (bounds.size () > 0)
                    {
                        UndoManager um = MainFrame.instance.undoManager;
                        boolean multi =  moved  &&  ! selection.isEmpty ();
                        ChangeAnnotations ca = new ChangeAnnotations (node, metadata);
                        if (! multi)
                        {
                            um.apply (ca);
                        }
                        else
                        {
                            CompoundEditView compound = new CompoundEditView (CompoundEditView.CLEAR_GRAPH);
                            um.addEdit (compound);
                            compound.addEdit (ca);  // delayed execution
                            for (GraphNode g : selection)
                            {
                                metadata = new MVolatile ();
                                bounds = metadata.childOrCreate ("gui", "bounds");
                                now = g.getBounds ();
                                if (dx != 0) bounds.set (now.x - parent.offset.x, "x");
                                if (dy != 0) bounds.set (now.y - parent.offset.y, "y");
                                compound.addEdit (new ChangeAnnotations (g.node, metadata));
                            }
                            um.endCompoundEdit ();
                            compound.redo ();
                        }
                    }
                }
            }

            takeFocus ();
        }

        public void actionPerformed (ActionEvent e)
        {
            mouseDragged (lastEvent);
        }
    }

    public static class RoundedBorder extends AbstractBorder
    {
        public int t;

        protected static Color background = Color.white;

        RoundedBorder (int thickness)
        {
            t = thickness;
        }

        public void paintBorder (Component c, Graphics g, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) g.create ();
            g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Shape border = new RoundRectangle2D.Double (x, y, width-1, height-1, t * 2, t * 2);

            g2.setPaint (background);
            g2.fill (border);

            GraphNode gn = (GraphNode) c;
            g2.setPaint (EquationTreeCellRenderer.getForegroundFor (gn.node, false));
            g2.draw (border);

            if (gn.open)
            {
                y += gn.hr.getLocation ().y + t * 2 - 1;
                Shape line = new Line2D.Double (x, y, x+width-1, y);
                g2.draw (line);
            }

            g2.dispose ();
        }

        public Insets getBorderInsets (Component c, Insets insets)
        {
            insets.left = insets.top = insets.right = insets.bottom = t;
            return insets;
        }

        public static void updateUI ()
        {
            background = UIManager.getColor ("Tree.background");
        }

        public int getCursor (MouseEvent me)
        {
            int x = me.getX ();
            int y = me.getY ();
            Component c = me.getComponent ();
            int w = c.getWidth ();
            int h = c.getHeight ();

            if (x < t)
            {
                if (y <  t    ) return Cursor.NW_RESIZE_CURSOR;
                if (y >= h - t) return Cursor.SW_RESIZE_CURSOR;
                return                 Cursor.W_RESIZE_CURSOR;
            }
            else if (x >= w - t)
            {
                if (y <  t    ) return Cursor.NE_RESIZE_CURSOR;
                if (y >= h - t) return Cursor.SE_RESIZE_CURSOR;
                return                 Cursor.E_RESIZE_CURSOR;
            }
            // x is in middle
            if (y <  t    ) return Cursor.N_RESIZE_CURSOR;
            if (y >= h - t) return Cursor.S_RESIZE_CURSOR;
            return                 Cursor.MOVE_CURSOR;
        }
    }
}
