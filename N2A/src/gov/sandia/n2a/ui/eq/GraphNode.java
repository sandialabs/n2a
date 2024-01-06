/*
Copyright 2019-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.MouseInputAdapter;
import javax.swing.tree.TreePath;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeIO;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.CompoundEditView;
import gov.sandia.n2a.ui.eq.undo.DeletePart;

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
    public    List<GraphEdge>     edgesIn             = new ArrayList<GraphEdge> ();
    protected Rectangle           pinOutBounds;  // Surrounds graphic representation of pins. Null if no out pins.
    protected Rectangle           pinInBounds;   // ditto for in pins
    protected String              side;          // Only non-null if this is a pin I/O block. In that case, this is one of "in" or "out".
    protected Point2D.Double      tempPosition;  // If this graph node has coordinate that are not stored in $meta, then they are stored here, in ems.

    public static final int        borderThickness = 5;
    protected static RoundedBorder border = new RoundedBorder (borderThickness);

    /**
        Constructs both regular graph nodes and pin I/O blocks.
        @param node If a child of container.part, then we are an ordinary graph node.
        If null, then we are a pin I/O block that uses a fake part.
        @param side For pin I/O blocks only, indicates which side of container.part we represent.
        This is the side as seen from outside of container.part.
    **/
    public GraphNode (GraphPanel parent, NodePart node, String side)
    {
        container = PanelModel.instance.panelEquations;  // "container" is merely a convenient shortcut

        MNode bounds;
        if (node == null)
        {
            node = new NodeIO (side, container.part);
            bounds = container.part.source.child ("$meta", "gui", "pin", "bounds", side);
        }
        else
        {
            bounds = node.source.child ("$meta", "gui", "bounds");
        }

        this.parent = parent;
        this.node   = node;
        this.side   = side;
        node.graph  = this;

        node.fakeRoot (true);
        if (container.view == PanelEquations.NODE)
        {
            panelEquationTree = new PanelEquationTree (container, true);
            panelEquationTree.loadPart (node);
        }

        // Internally, this class uses the null/non-null state of panelEquationsTree to indicated whether
        // container.view is NODE or a property panel mode.
        open =  panelEquationTree != null  &&  node.source.getBoolean ("$meta", "gui", "bounds", "open");

        title = new TitleRenderer ();
        title.getTreeCellRendererComponent (getEquationTree ().tree, node, false, open, false, -2, false);  // Configure JLabel with info from node.
        boolean focusable =  side == null;
        title.setFocusable (focusable);            // make focusable in general
        title.setRequestFocusEnabled (focusable);  // make focusable by mouse

        panelTitle = Lay.BL ("N", title);
        panelTitle.setOpaque (false);
        if (open) panelTitle.add (hr, BorderLayout.CENTER);
        Lay.BLtg (this, "N", panelTitle);
        if (open) add (panelEquationTree, BorderLayout.CENTER);
        setBorder (border);
        setOpaque (false);

        if (bounds != null) setLocation (getIdealLocation (bounds));
        updatePins ();

        ToolTipManager.sharedInstance ().registerComponent (this);
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

    public boolean isMinimized ()
    {
        if (container.editor.editingNode == node) return false;
        if (pinInBounds != null  ||  pinOutBounds != null) return false;  // Don't minimize a node with pins, because it would look weird.
        if (container.view == PanelEquations.NODE) return false;  // Don't minimize in node mode, because it would be ugly to expand from minimized to equation tree.
        boolean compartment =  node == null  ||  node.connectionBindings == null;
        return compartment ? container.minimizeCompartments : container.minimizeConnections;
    }

    /**
        Determines the offset, if any, between the pixel position of this node and its
        box on screen. The offset is only nonzero when the node is minimized.
    **/
    public void offset (Point p, int sign)
    {
        if (! isMinimized ()) return;
        Dimension d = title.getPreferredSize ();
        p.x += sign * (border.t + d.width  / 2);
        p.y += sign * (border.t + d.height / 2);
    }

    public void holdTempPosition ()
    {
        Point p = getLocation ();
        offset (p, -1);
        if (tempPosition == null) tempPosition = new Point2D.Double ();
        tempPosition.x = (p.x - parent.offset.x) / parent.em;
        tempPosition.y = (p.y - parent.offset.y) / parent.em;
    }

    /**
        Update bounds based on new zoom.
        Assumes that both parent.em and parent.offset have been updated already.
    **/
    public void rescale ()
    {
        title.UIupdated = true;
        if (panelEquationTree != null) panelEquationTree.tree.updateUI ();
        updatePins ();
        setSize (getPreferredSize ());

        setLocation (getIdealLocation (getBoundsNode ()));
    }

    public MNode getBoundsNode ()
    {
        GraphPanel gp = container.panelEquationGraph.graphPanel;
        if (this == gp.pinIn)  return container.part.source.child ("$meta", "gui", "pin", "bounds", "in");
        if (this == gp.pinOut) return container.part.source.child ("$meta", "gui", "pin", "bounds", "out");
        else                   return node.source.child ("$meta", "gui", "bounds");
    }

    public Point getIdealLocation (MNode bounds)
    {
        Point2D.Double position;
        if (bounds == null)
        {
            position = tempPosition;
            if (position == null) position = new Point2D.Double ();
        }
        else
        {
            position = new Point2D.Double ();
            position.x = (float) bounds.getDouble ("x");
            position.y = (float) bounds.getDouble ("y");
        }

        Point p = new Point ();
        p.x = (int) Math.round (position.x * parent.em) + parent.offset.x;
        p.y = (int) Math.round (position.y * parent.em) + parent.offset.y;
        offset (p, 1);

        return p;
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

    public Font getFont ()
    {
        if (parent == null) return super.getFont ();  // for startup
        return parent.getFont ();
    }

    public String getToolTipText ()
    {
        FontMetrics fm = getFontMetrics (MainFrame.instance.getFont ());
        return node.getToolTipText (fm);
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
            PanelEquationTree pet = container.panelEquationTree;
            container.active = pet;
            if (pet.root != node  &&  ! node.toString ().isEmpty ())  // Only load tree if node is not blank. Usually, a blank node is about to be deleted.
            {
                // Save current property panel UI state.
                if (pet.root != null)  // Can it ever be null?
                {
                    FocusCacheEntry fce = container.createFocus (pet.root);
                    if (pet.root.graph != null) fce.titleFocused = pet.root.graph.titleFocused;
                    fce.sp = pet.saveFocus (fce.sp);
                }

                // Load new part into property panel.
                pet.loadPart (node);
                FocusCacheEntry fce = container.createFocus (node);
                if (fce.sp != null) fce.sp.restore (pet.tree, false);
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
        if (container.locked)
        {
            setOpen (nextOpen);
        }
        else
        {
            MNode metadata = new MVolatile ();
            metadata.set (nextOpen, "gui", "bounds", "open");
            ChangeAnnotations ca = new ChangeAnnotations (node, metadata);
            ca.graph = true;
            ca.redo ();  // and don't record with undo manager.
        }
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
        title.getTreeCellRendererComponent (panelEquationTree.tree, node, focused || selected, open, false, -2, focused);
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
        if (isMinimized ())
        {
            int w = 2 * border.t;
            return new Dimension (w, w);
        }

        int w = 0;
        int h = 0;
        MNode bounds = node.source.child ("$meta", "gui", "bounds");
        if (bounds != null)
        {
            double em = parent.em;
            if (open)
            {
                MNode boundsOpen = bounds.child ("open");
                if (boundsOpen != null)
                {
                    w = (int) Math.round (boundsOpen.getDouble ("width")  * em);
                    h = (int) Math.round (boundsOpen.getDouble ("height") * em);
                }
            }
            else  // closed
            {
                w = (int) Math.round (bounds.getDouble ("width")  * em);
                h = (int) Math.round (bounds.getDouble ("height") * em);
            }
            
            if (w != 0  &&  h != 0) return new Dimension (w, h);
        }

        Dimension d = super.getPreferredSize ();  // Gets the layout manager's opinion.
        d.width  = Math.max (d.width,  w);
        d.height = Math.max (d.height, h);

        if (pinInBounds  != null) d.height = Math.max (d.height, pinInBounds .height + 2 * border.t);
        if (pinOutBounds != null) d.height = Math.max (d.height, pinOutBounds.height + 2 * border.t);

        // Don't exceed current size of viewport.
        // Should this limit be imposed on user settings as well?
        Dimension extent = ((JViewport) parent.getParent ()).getExtentSize ();
        d.width  = Math.min (d.width,  extent.width);
        d.height = Math.min (d.height, extent.height);

        return d;
    }

    public Point getCenter ()
    {
        Rectangle bounds = getBounds ();
        bounds.x += bounds.width / 2;
        bounds.y += bounds.height / 2;
        return bounds.getLocation ();
    }

    public void nudge (ActionEvent e, int dx, int dy)
    {
        if (container.locked) return;

        if ((e.getModifiers () & ActionEvent.CTRL_MASK) != 0)
        {
            dx *= 10;
            dy *= 10;
        }

        List<GraphNode> selection = container.panelEquationGraph.getSelection ();
        selection.remove (this);

        MNode metadata = new MVolatile ();
        NodePart np;
        MNode bounds;
        GraphPanel gp = container.panelEquationGraph.graphPanel;
        if (this == gp.pinIn)
        {
            np = container.part;
            bounds = metadata.childOrCreate ("gui", "pin", "bounds", "in");
        }
        else if (this == gp.pinOut)
        {
            np = container.part;
            bounds = metadata.childOrCreate ("gui", "pin", "bounds", "out");
        }
        else
        {
            np = node;
            bounds = metadata.childOrCreate ("gui", "bounds");
        }
        Point now = getLocation ();
        offset (now, -1);
        double em = parent.em;
        if (dx != 0  ||  np != node) bounds.setTruncated ((now.x - parent.offset.x + dx) / em, 2, "x");
        if (dy != 0  ||  np != node) bounds.setTruncated ((now.y - parent.offset.y + dy) / em, 2, "y");
        ChangeAnnotations ca = new ChangeAnnotations (np, metadata);
        ca.graph = true;

        UndoManager um = MainFrame.undoManager;
        if (selection.isEmpty ())
        {
            um.apply (ca);
        }
        else
        {
            CompoundEditView compound = new CompoundEditView (CompoundEditView.CLEAR_GRAPH);
            um.addEdit (compound);
            compound.addEdit (ca);  // delayed execution
            if (np == node) compound.leadPath = np.getKeyPath ();
            for (GraphNode g : selection)
            {
                metadata = new MVolatile ();
                if (g == gp.pinIn)
                {
                    np = container.part;
                    bounds = metadata.childOrCreate ("gui", "pin", "bounds", "in");
                }
                else if (g == gp.pinOut)
                {
                    np = container.part;
                    bounds = metadata.childOrCreate ("gui", "pin", "bounds", "out");
                }
                else
                {
                    np = g.node;
                    bounds = metadata.childOrCreate ("gui", "bounds");
                    if (compound.leadPath == null) compound.leadPath = np.getKeyPath ();
                }
                now = g.getLocation ();
                g.offset (now, -1);
                if (dx != 0  ||  np != node) bounds.setTruncated ((now.x - parent.offset.x + dx) / em, 2, "x");
                if (dy != 0  ||  np != node) bounds.setTruncated ((now.y - parent.offset.y + dy) / em, 2, "y");
                ca = new ChangeAnnotations (np, metadata);
                ca.graph = true;
                compound.addEdit (ca);
            }
            um.endCompoundEdit ();
            compound.redo ();
        }
    }

    public void updateTitle ()
    {
        node.setUserObject ();
        boolean focused = title.isFocusOwner ();
        title.getTreeCellRendererComponent (getEquationTree ().tree, node, focused || selected, open, false, -2, focused);
        animate ();
    }

    /**
        Apply any changes from $meta.gui
    **/
    public void updateGUI ()
    {
        title.updateSelected ();  // In case icon changed.
        updatePins ();

        // Determine new position
        Point p = new Point (parent.offset);
        MNode bounds = node.source.child ("$meta", "gui", "bounds");
        if (bounds != null)
        {
            double em = parent.em;
            p.x += (int) Math.round (bounds.getDouble ("x") * em);
            p.y += (int) Math.round (bounds.getDouble ("y") * em);
            if (panelEquationTree == null) offset (p, 1);
            else                           setOpen (bounds.getBoolean ("open"));
        }

        // Apply
        Dimension d = getPreferredSize ();  // Fetches updated width and height.
        Rectangle r = new Rectangle (p, d);
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

    public void repaintEdges ()
    {
        Rectangle paintRegion = new Rectangle (0, 0, -1, -1);
        for (GraphEdge ge : edgesIn ) paintRegion = paintRegion.union (ge.bounds);
        for (GraphEdge ge : edgesOut) paintRegion = paintRegion.union (ge.bounds);
        parent.repaint (paintRegion);
    }

    /**
        Sets bounds to current preferred location & size. Updates everything affected by the change.
    **/
    public void animate ()
    {
        Point p = getIdealLocation (getBoundsNode ());
        Rectangle next = new Rectangle (p, getPreferredSize ());
        if (getBounds () != next) animate (next);
    }

    /**
        Sets bounds and repaints portion of container that was exposed by move or resize.
    **/
    public void animate (Rectangle next)
    {
        Rectangle current = getBounds ();
        Rectangle currentWithPins = current;
        Rectangle nextWithPins    = next;
        if (pinInBounds != null)
        {
            // There may be a latent bug when the label of pin shrinks or the pin goes away. In that case,
            // we don't really keep track of the previous region (currentWithPins) properly, because we only
            // have the current size of the pin bounds. So far this hasn't caused any problems. Not sure why.

            // If node is sized correctly, the following should only change width and x, so this code could be simpler.

            pinInBounds.y = current.y + border.t;
            pinInBounds.x = current.x - pinInBounds.width;
            currentWithPins = currentWithPins.union (pinInBounds);
            pinInBounds.y = next.y + border.t;
            pinInBounds.x = next.x - pinInBounds.width;
            nextWithPins = nextWithPins.union (pinInBounds);
        }
        if (pinOutBounds != null)
        {
            pinOutBounds.y = current.y + border.t;
            pinOutBounds.x = current.x + current.width;
            currentWithPins = currentWithPins.union (pinOutBounds);
            pinOutBounds.y = next.y + border.t;
            pinOutBounds.x = next.x + next.width;
            nextWithPins = nextWithPins.union (pinOutBounds);
        }

        Rectangle paintRegion = nextWithPins.union (currentWithPins);
        setBounds (next);
        parent.layout.componentMoved (nextWithPins);

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

    public void updatePins ()
    {
        pinInBounds  = null;
        pinOutBounds = null;
        if (node.pinIn == null  &&  node.pinOut == null) return;

        FontMetrics fm = getFontMetrics (getFont ());
        int height = fm.getHeight () + 2 * GraphEdge.padNameTop;
        int boxWidth = height / 2;

        if (node.pinIn != null)
        {
            pinInBounds = new Rectangle ();
            pinInBounds.height = height * node.pinIn.size ();
            for (MNode c : node.pinIn) pinInBounds.width = Math.max (pinInBounds.width, fm.stringWidth (c.key ()));
            pinInBounds.width += boxWidth + 2 * GraphEdge.padNameSide;
        }

        if (node.pinOut != null)
        {
            pinOutBounds = new Rectangle ();
            pinOutBounds.height = height * node.pinOut.size ();
            for (MNode c : node.pinOut) pinOutBounds.width = Math.max (pinOutBounds.width, fm.stringWidth (c.key ()));
            pinOutBounds.width += boxWidth + 2 * GraphEdge.padNameSide;
        }
    }

    /**
        If the point falls on a pin, then return pin identifier. Otherwise return empty string.
        The identifier is in the same format used to mark connection endpoints: <in/out>.<pin name>
    **/
    public String findPinAt (Point p)
    {
        if (pinInBounds != null  &&  pinInBounds.contains (p))
        {
            int lineHeight = pinInBounds.height / node.pinIn.size ();
            int y = p.y - pinInBounds.y;
            MNode c = node.pinInOrder.get (y / lineHeight);
            return "in." + c.key ();
        }
        if (pinOutBounds != null  &&  pinOutBounds.contains (p))
        {
            int lineHeight = pinOutBounds.height / node.pinOut.size ();
            int y = p.y - pinOutBounds.y;
            MNode c = node.pinOutOrder.get (y / lineHeight);
            return "out." + c.key ();
        }
        return "";
    }

    public void paintPins (Graphics2D g2, Rectangle clip)
    {
        if (pinInBounds == null  &&  pinOutBounds == null) return;  // Early-out

        Rectangle   bounds     = getBounds ();
        FontMetrics fm         = getFontMetrics (getFont ());
        int         ascent     = fm.getAscent ();
        int         lineHeight = fm.getHeight () + 2 * GraphEdge.padNameTop;
        int         boxSize    = lineHeight / 2;

        if (pinInBounds != null)
        {
            pinInBounds.x = bounds.x - pinInBounds.width;
            pinInBounds.y = bounds.y + border.t;
            if (pinInBounds.intersects (clip))
            {
                int y = pinInBounds.y;
                for (MNode c : node.pinInOrder)
                {
                    paintPin (true, c, g2, bounds, fm, ascent, lineHeight, boxSize, y);
                    y += lineHeight;
                }
            }
        }

        if (pinOutBounds != null)
        {
            pinOutBounds.x = bounds.x + bounds.width;
            pinOutBounds.y = bounds.y + border.t;
            if (pinOutBounds.intersects (clip))
            {
                int y = pinOutBounds.y;
                for (MNode c : node.pinOutOrder)
                {
                    paintPin (false, c, g2, bounds, fm, ascent, lineHeight, boxSize, y);
                    y += lineHeight;
                }
            }
        }
    }

    public void paintPin (boolean in, MNode c, Graphics2D g2, Rectangle bounds, FontMetrics fm, int ascent, int lineHeight, int boxSize, int y)
    {
        String name = c.key ();

        int lineWidth = fm.stringWidth (name) + 2 * GraphEdge.padNameSide;
        Rectangle textBox = new Rectangle ();
        if (in) textBox.x = bounds.x - boxSize - lineWidth;
        else    textBox.x = bounds.x + bounds.width + boxSize;
        textBox.y = y;
        textBox.width = lineWidth;
        textBox.height = lineHeight;
        int textX = textBox.x + GraphEdge.padNameSide;
        int textY = textBox.y + GraphEdge.padNameTop + ascent;
        Rectangle box = new Rectangle ();
        if (in) box.x = bounds.x - boxSize;
        else    box.x = bounds.x + bounds.width - 1;  // The extra 1-pixel offset is to move the square pin box under the edge of the node. This is true of both boxes and both labels, but their natural offsets already work for this.
        box.y = y + boxSize / 2;
        box.width = box.height = boxSize;

        Color boxBorder = Color.black;
        String colorName = c.get ("color");
        if (! colorName.isEmpty ())
        {
            try {boxBorder = Color.decode (colorName);}
            catch (NumberFormatException e) {}
        }

        Color boxFill;
        boolean bound = c.getFlag ("bound");
        if (bound) boxFill = boxBorder;
        else       boxFill = new Color (boxBorder.getRed (), boxBorder.getGreen (), boxBorder.getBlue (), 0x40);  // Semi-transparent version of boxBorder

        g2.setColor (new Color (0xD0FFFFFF, true));
        g2.fill (textBox);
        g2.setColor (Color.black);
        g2.drawString (name, textX, textY);
        g2.setColor (boxFill);
        g2.fill (box);
        g2.setColor (boxBorder);
        g2.draw (box);
    }

    public class TitleRenderer extends EquationTreeCellRenderer implements CellEditorListener
    {
        protected Component editingComponent;
        protected boolean   UIupdated;

        public TitleRenderer ()
        {
            nontree = true;
            bigIcon = true;

            setTransferHandler (container.transferHandler);
            ToolTipManager.sharedInstance ().registerComponent (this);

            InputMap inputMap = getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("UP"),                "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("DOWN"),              "selectNext");
            inputMap.put (KeyStroke.getKeyStroke ("LEFT"),              "close");
            inputMap.put (KeyStroke.getKeyStroke ("RIGHT"),             "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl UP"),           "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl DOWN"),         "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl LEFT"),         "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl RIGHT"),        "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("shift UP"),          "moveUp");
            inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"),        "moveDown");
            inputMap.put (KeyStroke.getKeyStroke ("shift LEFT"),        "moveLeft");
            inputMap.put (KeyStroke.getKeyStroke ("shift RIGHT"),       "moveRight");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl UP"),     "moveUp");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl DOWN"),   "moveDown");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl LEFT"),   "moveLeft");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl RIGHT"),  "moveRight");
            inputMap.put (KeyStroke.getKeyStroke ("shift DELETE"),      "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl X"),            "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl INSERT"),       "copy");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl C"),            "copy");
            inputMap.put (KeyStroke.getKeyStroke ("shift INSERT"),      "paste");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl V"),            "paste");
            inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl EQUALS"),       "add");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl 1"),            "addPart");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl 2"),            "addVariable");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl 3"),            "addEquation");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl 4"),            "addAnnotation");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl 5"),            "addReference");
            inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
            inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
            inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("F2"),                "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl D"),      "drillUp");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl D"),            "drillDown");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl O"),            "outsource");
            inputMap.put (KeyStroke.getKeyStroke ("SPACE"),             "toggleSelection");

            ActionMap actionMap = getActionMap ();
            actionMap.put ("nothing", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    // Literally, we do nothing.
                    // This is necessary to kill the keystroke.
                    // In the case of up-arrow, we don't want it to move the scroll pane.
                }
            });
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
                    List<GraphNode> selection = container.panelEquationGraph.getSelection ();
                    selection.remove (GraphNode.this);  // Ensure we are at the end of the list ...
                    selection.add (GraphNode.this);

                    if (selection.size () > 1)  // Create a new connection part.
                    {
                        List<NodePart> query = new ArrayList<NodePart> ();
                        for (GraphNode g : selection) query.add (g.node);
                        PanelModel.instance.panelSearch.search (query);
                    }
                    else if (open)
                    {
                        panelEquationTree.addAtSelected ("");  // No selection should be active, so this should default to root (same as our "node").
                    }
                    else  // closed, so add new peer part in graph area near this node
                    {
                        Point p = GraphNode.this.getLocation ();
                        Point2D.Double location = new Point2D.Double ();
                        location.x = (p.x - parent.offset.x) / parent.em + 8;
                        location.y = (p.y - parent.offset.y) / parent.em + 8;
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
            actionMap.put ("addPart", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    ActionEvent e2 = new ActionEvent (TitleRenderer.this, ActionEvent.ACTION_FIRST, "Part");
                    container.listenerAdd.actionPerformed (e2);
                }
            });
            actionMap.put ("addVariable", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    ActionEvent e2 = new ActionEvent (TitleRenderer.this, ActionEvent.ACTION_FIRST, "Variable");
                    container.listenerAdd.actionPerformed (e2);
                }
            });
            actionMap.put ("addEquation", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    ActionEvent e2 = new ActionEvent (TitleRenderer.this, ActionEvent.ACTION_FIRST, "Equation");
                    container.listenerAdd.actionPerformed (e2);
                }
            });
            actionMap.put ("addAnnotation", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    ActionEvent e2 = new ActionEvent (TitleRenderer.this, ActionEvent.ACTION_FIRST, "Annotation");
                    container.listenerAdd.actionPerformed (e2);
                }
            });
            actionMap.put ("addReference", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    ActionEvent e2 = new ActionEvent (TitleRenderer.this, ActionEvent.ACTION_FIRST, "Reference");
                    container.listenerAdd.actionPerformed (e2);
                }
            });
            actionMap.put ("delete", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    delete ();
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
                    setSelected (! GraphNode.this.selected);
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

                // Notice that this method is only called if a drag did not occur.
                // On drag, resizeListener.mouseReleased() does all the work.
                public void mouseClicked (MouseEvent me)
                {
                    if (side != null)  // This is a pin IO block.
                    {
                        // Shift focus to $meta.gui.pin.side in parent part.
                        container.switchFocus (false, false);  // Select the parent tree.
                        NodeBase metadata = container.part.child ("$meta");
                        if (metadata != null)
                        {
                            metadata = AddAnnotation.findClosest (metadata, "gui", "pin", side);
                            container.getParentEquationTree ().tree.setSelectionPath (new TreePath (metadata.getPath ()));
                        }
                        return;  // Don't allow edit or drill-down.
                    }

                    int x = me.getX ();
                    int y = me.getY ();
                    int clicks = me.getClickCount ();
                    boolean extendSelection = me.isShiftDown ();

                    if (SwingUtilities.isRightMouseButton (me)  ||  me.isControlDown ()  &&  Host.isMac ())
                    {
                        if (clicks == 1)  // Show popup menu
                        {
                            container.panelEquationGraph.clearSelection ();
                            switchFocus (true, false);
                            container.menuPopup.show (title, x, y);
                        }
                    }
                    else if (SwingUtilities.isLeftMouseButton (me))
                    {
                        if (clicks == 1)  // Open/close
                        {
                            int iconWidth = node.getIcon (open).getIconWidth ();  // "open" isn't actually important for root node, as NodePart doesn't currently change appearance.
                            if (x < iconWidth)
                            {
                                if (panelEquationTree != null) toggleOpen ();
                            }
                            if (extendSelection)
                            {
                                // See similar code in resizeListener.mouseReleased()
                                // In case we are not the focus, ensure that the current focus is also selected.
                                GraphNode g = PanelModel.getGraphNode (KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ());
                                if (g != GraphNode.this)
                                {
                                    if (g != null) g.setSelected (true);
                                    setSelected (! GraphNode.this.selected);
                                }
                            }
                            else
                            {
                                container.panelEquationGraph.clearSelection ();
                                switchFocus (true, false);
                            }
                        }
                        else if (clicks == 2)  // Drill down
                        {
                            container.drill (node);
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
                    titleFocused = true;  // When resizeListener processes this event after a drag, it calls takeFocus(). The focus should always go to title when title was clicked.
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
                    getTreeCellRendererComponent (getEquationTree ().tree, node, true, open, false, -2, true);
                    restoreFocus ();  // does repaint
                    container.updateHighlights (node);

                    if (parent.focus != GraphNode.this)
                    {
                        repaintEdges ();
                        if (parent.focus != null) parent.focus.repaintEdges ();
                        parent.focus = GraphNode.this;
                    }
                }

                public void focusLost (FocusEvent e)
                {
                    Component other = e.getOppositeComponent ();
                    if (other != null)  // Focus remains inside application.
                    {
                        GraphNode g = PanelModel.getGraphNode (other);
                        if (g == null) container.panelEquationGraph.clearSelection ();  // Next focus in not a graph node, so unset all selections. This avoids visual confusion.
                    }

                    getTreeCellRendererComponent (getEquationTree ().tree, node, GraphNode.this.selected, open, false, -2, false);
                    GraphNode.this.repaint ();

                    if (parent.focus == GraphNode.this)
                    {
                        parent.focus = null;
                        repaintEdges ();
                    }
                }
            });
        }

        public void updateUI ()
        {
            super.updateUI ();
            UIupdated = true;
        }

        @Override
        public Font getBaseFont (JTree tree)
        {
            return parent.scaledTreeFont;
        }

        public String getToolTipText ()
        {
            FontMetrics fm = getFontMetrics (MainFrame.instance.getFont ());  // Don't use our own font, because it could be scaled.
            return node.getToolTipText (fm);
        }

        public Dimension getPreferredSize ()
        {
            if (UIupdated)
            {
                UIupdated = false;
                boolean focused = isFocusOwner ();
                getTreeCellRendererComponent (getEquationTree ().tree, node, GraphNode.this.selected || focused, open, false, -2, focused);
            }
            return super.getPreferredSize ();
        }

        public void updateSelected ()
        {
            boolean focused = isFocusOwner ();
            getTreeCellRendererComponent (getEquationTree ().tree, node, GraphNode.this.selected || focused, open, false, -2, focused);
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
            if (isMinimized ()) panelTitle.setSize (title.getPreferredSize ());  // When minimized, panelTitle is zero-sized, but we need a proper width to set up editor.
            editingComponent = container.editor.getTitleEditorComponent (getEquationTree ().tree, node, this, open);
            panelTitle.add (editingComponent, BorderLayout.NORTH, 0);  // displaces this renderer from the layout manager's north slot
            setVisible (false);  // hide this renderer

            GraphNode.this.setLocation (getIdealLocation (getBoundsNode()));
            GraphNode.this.setSize (GraphNode.this.getPreferredSize ());
            GraphNode.this.validate ();
            parent.scrollRectToVisible (GraphNode.this.getBounds ());
            GraphNode.this.repaint ();
            container.editor.editingComponent.requestFocusInWindow ();  // The "editingComponent" returned above is actually just the container for the real editing component.
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

        /**
            Handle keyboard action delete.
            Removes this graph node, along with any others that are currently selected.
            This is a separate function so it can also be called by the transfer handler for cut operations.
        **/
        public void delete ()
        {
            List<GraphNode> selection = parent.getSelection ();
            selection.remove (GraphNode.this);
            selection.remove (container.panelEquationGraph.graphPanel.pinIn);
            selection.remove (container.panelEquationGraph.graphPanel.pinOut);
            container.panelEquationGraph.clearSelection ();  // In case pinIn or pinOut were selected. After delete, nothing should be selected.

            UndoManager um = MainFrame.undoManager;
            if (selection.isEmpty ())
            {
                um.apply (new DeletePart (node, false));
            }
            else
            {
                selection.add (GraphNode.this);  // Now at end of list, which is where we want it.
                CompoundEditView compound = new CompoundEditView (CompoundEditView.CLEAR_GRAPH);
                compound.leadPath = node.getKeyPath ();
                um.addEdit (compound);
                int last = selection.size () - 1;
                int i = 0;
                for (GraphNode g : selection)
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
    }

    public class ResizeListener extends MouseInputAdapter implements ActionListener
    {
        int             cursor;
        Point           start;
        Dimension       min;
        Rectangle       old;
        boolean         connect;
        GraphEdge       edge;  // Paints edge when in dragging in connect mode.
        boolean         dragged;
        MouseEvent      lastEvent;
        Timer           timer = new Timer (100, this);
        List<GraphNode> selection;

        public void mouseClicked (MouseEvent me)
        {
            // See mouse listener on title renderer for similar code.
            if (side != null)  // This is a pin IO block.
            {
                // Shift focus to $meta.gui.pin.side in parent part.
                container.switchFocus (false, false);  // Select the parent tree.
                NodeBase metadata = container.part.child ("$meta");
                if (metadata != null)
                {
                    metadata = AddAnnotation.findClosest (metadata, "gui", "pin", side);
                    container.getParentEquationTree ().tree.setSelectionPath (new TreePath (metadata.getPath ()));
                }
                return;  // Don't allow edit or drill-down.
            }

            if (SwingUtilities.isLeftMouseButton (me))
            {
                if (me.getClickCount () == 2)  // Drill down
                {
                    container.drill (node);
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
            if (SwingUtilities.isMiddleMouseButton (me))
            {
                me.translatePoint (getX (), getY ());
                parent.mouseListener.mousePressed (me);
                return;
            }
            else if (SwingUtilities.isRightMouseButton (me)  ||  me.isControlDown ())
            {
                takeFocusOnTitle ();  // Because this method won't be called unless the tree portion is empty.
                container.menuPopup.show (GraphNode.this, me.getX (), me.getY ());
                return;
            }

            if (container.locked) return;
            if (! SwingUtilities.isLeftMouseButton (me)) return;
            boolean extendSelection = me.isShiftDown ();

            // All mouse event coordinates are relative to the bounds of this component.
            parent.setComponentZOrder (GraphNode.this, 0);
            start   = me.getPoint ();
            min     = getMinimumSize ();
            old     = getBounds ();
            connect = me.isShiftDown ();
            edge    = null;
            dragged = false;
            cursor  = border.getCursor (me);
            setCursor (Cursor.getPredefinedCursor (cursor));

            // To understand this code, note that there are three kinds of graph nodes (for the purpose of dragging):
            // focused  -- has keyboard focus and a special mark; also appears as if selected, but might not actually be flagged as part of selection.
            // selected -- tagged as part of the selection
            // targeted -- The node that actually received the mousePressed event, and which might get dragged.
            // Focused should move with the selection, whether it is actually selected or not.
            // If targeted is neither focused nor selected, then only targeted should be moved.
            //     Selection is cleared and focus is ignored. Afterward, targeted becomes new focus.
            selection = container.panelEquationGraph.getSelection ();
            if (selection.contains (GraphNode.this))  // Target is selected.
            {
                // The code that handles drag expects that the target is not included in "selection".
                selection.remove (GraphNode.this);

                // If focused node is not the target, then ensure it gets moved along with selection.
                GraphNode g = PanelModel.getGraphNode (KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ());
                if (g != null  &&  g != GraphNode.this  &&  ! selection.contains (g)) selection.add (g);
            }
            else  // Target is not selected.
            {
                if (! title.isFocusOwner ()  &&  ! extendSelection)  // Target is also not the focus, and we don't wish to extend selection.
                {
                    // Clear selection and don't drag it along with target.
                    container.panelEquationGraph.clearSelection ();
                    selection.clear ();
                }
            }
        }

        public void mouseDragged (MouseEvent me)
        {
            if (SwingUtilities.isMiddleMouseButton (me))
            {
                me.translatePoint (getX (), getY ());
                parent.mouseListener.mouseDragged (me);
                return;
            }

            if (start == null) return;
            dragged = true;

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
                    Point     c = me.getPoint ();
                    Dimension d = getSize ();
                    if (c.x < 0  ||  c.x > d.width  ||  c.y < 0  ||  c.y > d.height)
                    {
                        // Create and install edge
                        edge = new GraphEdge (GraphNode.this, null, "");
                        edge.anchor = new Point (start);  // Position in this component where drag started.
                        edge.tip = new Vector2 (0, 0);  // This is normally created by GraphEdge.updateShape(), but we don't call that first.
                        parent.edges.add (edge);
                    }
                }
                if (edge != null)
                {
                    int nx = Math.max (x + me.getX (), 0);
                    int ny = Math.max (y + me.getY (), 0);
                    edge.animate (new Point (nx, ny));
                }
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

            if (SwingUtilities.isMiddleMouseButton (me))
            {
                me.translatePoint (getX (), getY ());
                parent.mouseListener.mouseReleased (me);
            }
            else if (SwingUtilities.isLeftMouseButton (me))
            {
                UndoManager um = MainFrame.undoManager;
                if (connect)
                {
                    if (edge == null)  // extend selection, because mouse never left the node boundary
                    {
                        if (dragged)  // and therefore mouseClicked() in TitleRenderer will not be called.
                        {
                            // In case we are not the focus, ensure that the current focus is also selected.
                            GraphNode g = PanelModel.getGraphNode (KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ());
                            if (g != GraphNode.this)
                            {
                                if (g != null) g.setSelected (true);
                                setSelected (! selected);
                            }
                        }
                    }
                    else  // actually connect, because mouse left node boundary
                    {
                        parent.edges.remove (edge);
                        parent.repaint (edge.bounds);
                        edge = null;
                        container.panelEquationGraph.clearSelection ();

                        Point p = new Point (getX () + me.getX (), getY () + me.getY ());
                        GraphNode gn = parent.findNodeAt (p, true);
                        if (gn != null)
                        {
                            String pinNew = gn.findPinAt (p);
                            if (gn.node instanceof NodeIO)
                            {
                                if (node.source.child ("$meta", "gui", "pin") == null)
                                {
                                    String pin = "";
                                    boolean OK;
                                    if (gn == container.panelEquationGraph.graphPanel.pinIn)
                                    {
                                        OK =  node.connectionBindings != null;
                                    }
                                    else  // graphPanel.pinOut
                                    {
                                        OK =  node.connectionBindings == null;
                                    }
                                    if (OK  &&  ! pinNew.isEmpty ())
                                    {
                                        String pieces[] = pinNew.split ("\\.", 2);
                                        String side = pieces[0];
                                        pin         = pieces[1];
                                        if (gn == container.panelEquationGraph.graphPanel.pinIn)
                                        {
                                            OK = side.equals ("out");
                                        }
                                        else  // graphPanel.pinOut
                                        {
                                            OK = side.equals ("in");
                                        }
                                    }
                                    if (OK)
                                    {
                                        MNode metadata = new MVolatile ();
                                        metadata.set (pin, "gui", "pin");
                                        // If topic needs to be set, the user must do it manually.
                                        um.apply (new ChangeAnnotations (node, metadata));
                                    }
                                }
                            }
                            else if (pinNew.isEmpty ())  // NodePart
                            {
                                List<NodePart> query = new ArrayList<NodePart> ();
                                query.add (node);
                                query.add (gn.node);
                                PanelModel.instance.panelSearch.search (query);
                            }
                        }
                    }
                }
                else if (cursor != Cursor.DEFAULT_CURSOR)
                {
                    // Store new bounds in metadata
                    MNode metadata = new MVolatile ();
                    NodePart np;
                    MNode bounds;
                    Rectangle now = getBounds ();
                    boolean moved =  now.x != old.x  ||  now.y != old.y;
                    GraphPanel gp = container.panelEquationGraph.graphPanel;
                    double em = parent.em;
                    if (GraphNode.this == gp.pinIn)
                    {
                        np = container.part;
                        bounds = metadata.childOrCreate ("gui", "pin", "bounds", "in");
                    }
                    else if (GraphNode.this == gp.pinOut)
                    {
                        np = container.part;
                        bounds = metadata.childOrCreate ("gui", "pin", "bounds", "out");
                    }
                    else
                    {
                        np = node;
                        bounds = metadata.childOrCreate ("gui", "bounds");
                        if (open)
                        {
                            MNode boundsOpen = bounds.childOrCreate ("open");
                            if (now.width  != old.width ) boundsOpen.setTruncated (now.width  / em, 2, "width");
                            if (now.height != old.height) boundsOpen.setTruncated (now.height / em, 2, "height");
                            if (boundsOpen.size () == 0) bounds.clear ("open");
                        }
                        else
                        {
                            if (now.width  != old.width ) bounds.setTruncated (now.width  / em, 2, "width");
                            if (now.height != old.height) bounds.setTruncated (now.height / em, 2, "height");
                        }
                    }
                    if (moved)
                    {
                        Point p = now.getLocation ();
                        offset (p, -1);
                        bounds.setTruncated ((p.x - parent.offset.x) / em, 2, "x");
                        bounds.setTruncated ((p.y - parent.offset.y) / em, 2, "y");
                    }
                    if (bounds.isEmpty ())
                    {
                        takeFocusOnTitle ();
                    }
                    else
                    {
                        boolean multi =  moved  &&  ! selection.isEmpty ();
                        ChangeAnnotations ca = new ChangeAnnotations (np, metadata);
                        ca.graph = true;
                        if (! multi)
                        {
                            um.apply (ca);
                            takeFocus ();
                        }
                        else
                        {
                            CompoundEditView compound = new CompoundEditView (CompoundEditView.CLEAR_GRAPH);
                            um.addEdit (compound);
                            compound.addEdit (ca);  // delayed execution
                            if (np == node) compound.leadPath = np.getKeyPath ();
                            for (GraphNode g : selection)
                            {
                                metadata = new MVolatile ();
                                if (g == gp.pinIn)
                                {
                                    np = container.part;
                                    bounds = metadata.childOrCreate ("gui", "pin", "bounds", "in");
                                }
                                else if (g == gp.pinOut)
                                {
                                    np = container.part;
                                    bounds = metadata.childOrCreate ("gui", "pin", "bounds", "out");
                                }
                                else
                                {
                                    np = g.node;
                                    bounds = metadata.childOrCreate ("gui", "bounds");
                                    if (compound.leadPath == null) compound.leadPath = np.getKeyPath ();
                                }
                                Point p = g.getLocation ();
                                g.offset (p, -1);
                                bounds.setTruncated ((p.x - parent.offset.x) / em, 2, "x");
                                bounds.setTruncated ((p.y - parent.offset.y) / em, 2, "y");
                                ca = new ChangeAnnotations (np, metadata);
                                ca.graph = true;
                                compound.addEdit (ca);
                            }
                            um.endCompoundEdit ();
                            compound.redo ();
                        }
                    }
                }
            }
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

            GraphNode gn = (GraphNode) c;
            boolean minimized = gn.isMinimized ();

            Shape border;
            if (minimized) border = new Rectangle2D.Double (x, y, width-1, height-1);  // Square border looks better when minimized.
            else           border = new RoundRectangle2D.Double (x, y, width-1, height-1, t * 2, t * 2);

            boolean selected = gn.title.selected;
            boolean hasFocus = gn.title.hasFocus;
            if (minimized  &&  (selected  ||  hasFocus)) // Because title doesn't show in minimized, so we need some way to indicate selected state.
            {
                if (EquationTreeCellRenderer.backgroundSelected == null)  // Use background color.
                {
                    g2.setPaint (EquationTreeCellRenderer.colorBackgroundSelected);
                    g2.fill (border);
                }
                else  // Use background painter. Compare with EquationTreeCellRenderer.paint()
                {
                    if (hasFocus)
                    {
                        if (selected) EquationTreeCellRenderer.backgroundFocusedSelected.paint (g2, gn, width, height);
                        else          EquationTreeCellRenderer.backgroundFocused        .paint (g2, gn, width, height);
                    }
                    else if (selected)  // and not focused
                    {
                        EquationTreeCellRenderer.backgroundSelected.paint (g2, gn, width, height);
                    }
                }
            }
            else  // Use standard background.
            {
                g2.setPaint (background);
                g2.fill (border);
            }

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
            if (! PanelModel.instance.panelEquations.enableResize) return Cursor.MOVE_CURSOR;

            Component c = me.getComponent ();
            if (c instanceof GraphNode)  // This should always be true.
            {
                GraphNode gn = (GraphNode) c;
                if (gn.isMinimized ()) return Cursor.MOVE_CURSOR;  // Don't allow resize on a minimized node.
            }

            int x = me.getX ();
            int y = me.getY ();
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
