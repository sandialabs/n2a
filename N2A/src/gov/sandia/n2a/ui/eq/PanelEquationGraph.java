/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.ViewportLayout;
import javax.swing.border.LineBorder;
import javax.swing.event.MouseInputAdapter;
import javax.swing.tree.TreePath;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.NTextField;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddPart;
import gov.sandia.n2a.ui.eq.undo.AddVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;
import gov.sandia.n2a.ui.eq.undo.DeletePart;
import gov.sandia.n2a.ui.eq.undo.DeleteVariable;
import gov.sandia.n2a.ui.images.ImageUtil;

@SuppressWarnings("serial")
public class PanelEquationGraph extends JScrollPane
{
    protected PanelEquations container;
    protected GraphPanel     graphPanel;
    protected JViewport      vp;  // for convenience
    protected ColoredBorder  border;

    protected static Color background = new Color (0xF0F0F0);  // light gray

    public PanelEquationGraph (PanelEquations container)
    {
        this.container = container;
        graphPanel = new GraphPanel ();
        setViewportView (graphPanel);
        border = new ColoredBorder ();
        setBorder (border);

        setTransferHandler (new GraphTransferHandler ());

        vp = getViewport ();
    }

    public void loadPart ()
    {
        graphPanel.clear ();
        graphPanel.load ();
    }

    public void reloadPart ()
    {
        graphPanel.clearParts ();
        graphPanel.load ();
        repaint ();
    }

    public void clear ()
    {
        container.active = null;  // on the presumption that container.panelEquationGraph was most recently on display. This function is only called in that case.
        graphPanel.clear ();
    }

    public void updateLock ()
    {
        graphPanel.updateLock ();
    }

    public Point saveFocus ()
    {
        Point focus = vp.getViewPosition ();
        focus.x -= graphPanel.offset.x;
        focus.y -= graphPanel.offset.y;
        if (! container.locked  &&  container.part != null)
        {
            // Check if offset has actually changed. This is a nicety to avoid modifying models unless absolutely necessary.
            Point old = new Point ();
            MNode parent = container.part.source.child ("$metadata", "gui", "bounds", "parent");
            if (parent != null)
            {
                old.x = parent.getInt ("x");
                old.y = parent.getInt ("y");
            }
            if (! focus.equals (old))
            {
                parent = container.part.source.childOrCreate ("$metadata", "gui", "bounds", "parent");
                if (focus.x != old.x) parent.set (focus.x, "x");
                if (focus.y != old.y) parent.set (focus.y, "y");
            }
        }
        return focus;
    }

    public void takeFocus (FocusCacheEntry fce)
    {
        // Select first node to focus.
        GraphNode gn = graphPanel.findNode (fce.subpart);
        if (gn == null  &&  graphPanel.getComponentCount () > 0)
        {
            Component c = PanelModel.instance.getFocusTraversalPolicy ().getFirstComponent (graphPanel);
            if (c == null) return;  // This can happen if the Models tab isn't currently visible, for example when working in Settings:Repositories and a reload is triggered.
            c = c.getParent ();
            if (! (c instanceof GraphNode)) c = c.getParent ();
            if (   c instanceof GraphNode ) gn = (GraphNode) c;
        }

        if (gn != null)
        {
            fce = container.createFocus (gn.node);
            gn.titleFocused = fce.titleFocused;
            gn.takeFocus ();  // possibly change the viewport position set above
        }
    }

    public void restoreViewportPosition (FocusCacheEntry fce)
    {
        Point focus = new Point ();  // (0,0)
        if (fce.position == null)
        {
            MPart parent = (MPart) container.part.source.child ("$metadata", "gui", "bounds", "parent");
            if (parent != null)
            {
                focus.x = parent.getOrDefault (0, "x");
                focus.y = parent.getOrDefault (0, "y");
            }
        }
        else
        {
            focus.x = fce.position.x;
            focus.y = fce.position.y;
        }

        focus.x += graphPanel.offset.x;
        focus.y += graphPanel.offset.y;
        graphPanel.layout.shiftViewport (focus);
    }

    /**
        Apply changes in metadata.
    **/
    public void updateGUI ()
    {
        Point focus = vp.getViewPosition ();
        focus.x -= graphPanel.offset.x;
        focus.y -= graphPanel.offset.y;

        MPart parent = (MPart) container.part.source.child ("$metadata", "gui", "bounds", "parent");
        if (parent != null)
        {
            focus.x = parent.getOrDefault (focus.x, "x");
            focus.y = parent.getOrDefault (focus.y, "y");
        }

        focus.x += graphPanel.offset.x;
        focus.y += graphPanel.offset.y;
        graphPanel.layout.shiftViewport (focus);
    }

    public void updatePins ()
    {
        graphPanel.updatePins ();
    }

    public GraphNode getPinOut ()
    {
        return graphPanel.pinOut;
    }

    public void updateFilterLevel ()
    {
        graphPanel.updateFilterLevel ();
    }

    /**
        Gives the coordinates of the center of the graph panel, in a form that is suitable for storing in a part.
    **/
    public Point getCenter ()
    {
        Point     result = vp.getViewPosition ();
        Dimension extent = vp.getExtentSize ();
        result.x += extent.width  / 2 - graphPanel.offset.x;
        result.y += extent.height / 2 - graphPanel.offset.y;
        return result;
    }

    public Dimension getExtentSize ()
    {
        return vp.getExtentSize ();
    }

    public Point getViewPosition ()
    {
        return vp.getViewPosition ();
    }

    /**
        @return The amount to add to stored coordinates to convert them to current view coordinates.
        Don't modify this object.
    **/
    public Point getOffset ()
    {
        return graphPanel.offset;
    }

    public void addPart (NodePart node)
    {
        if (node.getParent () == container.part) graphPanel.addPart (node);
    }

    public void removePart (NodePart node, boolean holdFocusInGraph)
    {
        if (node.graph == null) return;
        if (container.active == node.getTree ()) container.active = null;  // In case this graph panel loses focus completely.

        // Try to keep focus inside graph area.
        if (holdFocusInGraph)
        {
            PanelModel pm = PanelModel.instance;
            FocusTraversalPolicy ftp = pm.getFocusTraversalPolicy ();
            Component c = ftp.getComponentAfter (pm, node.graph.title);
            while (c != null  &&  ! (c instanceof GraphNode)) c = c.getParent ();
            if (c == null)  // next focus is not in a graph node, so outside of this equation graph
            {
                c = ftp.getComponentBefore (pm, node.graph.title);
                PanelEquations pe = pm.panelEquations;
                if (c == pe.breadcrumbRenderer  ||  c == pe.panelParent.panelEquationTree.tree)
                {
                    pe.getTitleFocus ().requestFocusInWindow ();
                }
                else
                {
                    while (c != null  &&  ! (c instanceof GraphNode)) c = c.getParent ();
                }
            }
            if (c instanceof GraphNode) ((GraphNode) c).takeFocus ();
        }

        graphPanel.removePart (node);  // If node still has focus, then default focus cycle applies.
    }

    /**
        Sets all selected graph nodes to unselected.
    **/
    public void clearSelection ()
    {
        graphPanel.clearSelection ();
    }

    public List<GraphNode> getSelection ()
    {
        return graphPanel.getSelection ();
    }

    /**
        Called by ChangePart to apply name change to an existing graph node.
        Note that underride implies several other cases besides simple name change.
        Those cases are handled by addPart() and removePart().
    **/
    public void updatePart (NodePart node)
    {
        if (node.graph != null) node.graph.updateTitle ();
    }

    public void reconnect ()
    {
        graphPanel.rebuildEdges ();
    }

    public boolean isEmpty ()
    {
        return graphPanel.getComponentCount () == 0;
    }

    public void updateUI ()
    {
        super.updateUI ();
        GraphNode.RoundedBorder.updateUI ();
        GraphParent.RoundedBottomBorder.updateUI ();
        background = UIManager.getColor ("ScrollPane.background");
    }

    public JViewport createViewport ()
    {
        return new JViewport ()
        {
            public LayoutManager createLayoutManager ()
            {
                return new ViewportLayout ()
                {
                    public void layoutContainer (Container parent)
                    {
                        // The original version of this code in OpenJDK moves the view if it is smaller than the viewport extent.
                        // Moving the viewport would disrupt user interaction, so we only set its size.

                        Dimension size   = graphPanel.getPreferredSize ();
                        Point     p      = vp.getViewPosition ();
                        Dimension extent = vp.getExtentSize ();
                        int visibleWidth  = size.width  - p.x;
                        int visibleHeight = size.height - p.y;
                        size.width  += Math.max (0, extent.width  - visibleWidth);
                        size.height += Math.max (0, extent.height - visibleHeight);
                        vp.setViewSize (size);
                    }
                };
            }
        };
    }

    public int getHorizontalScrollBarPolicy ()
    {
        if (vp != null)
        {
            Point p = vp.getViewPosition ();
            if (p.x > 0) return HORIZONTAL_SCROLLBAR_ALWAYS;
        }
        return HORIZONTAL_SCROLLBAR_AS_NEEDED;
    }

    public int getVerticalScrollBarPolicy ()
    {
        if (vp != null)
        {
            Point p = vp.getViewPosition ();
            if (p.y > 0) return VERTICAL_SCROLLBAR_ALWAYS;
        }
        return VERTICAL_SCROLLBAR_AS_NEEDED;
    }

    public class ColoredBorder extends LineBorder
    {
        ColoredBorder ()
        {
            super (Color.black);
        }

        public void paintBorder (Component c, Graphics g, int x, int y, int width, int height)
        {
            if (container.part == null) lineColor = Color.black;  // part can be null if no model is currently loaded
            else                        lineColor = EquationTreeCellRenderer.getForegroundFor (container.part, false);
            super.paintBorder (c, g, x, y, width, height);
        }
    }

    public class GraphPanel extends JPanel
    {
        protected GraphLayout        layout;                               // For ease of access, to avoid calling getLayout() all the time.
        protected GraphMouseListener mouseListener;
        protected List<GraphEdge>    edges  = new ArrayList<GraphEdge> (); // Note that GraphNodes are stored directly as Swing components.
        public    Point              offset = new Point ();                // Offset from persistent coordinates to viewport coordinates. Add this to a stored (x,y) value to get non-negative coordinates that can be painted.
        protected JPopupMenu         arrowMenu;
        protected GraphEdge          arrowEdge;                            // Most recent edge when arrowMenu was activated.
        protected Point              popupLocation;
        protected JPopupMenu         pinMenu;
        protected GraphNode          pinIn;
        protected GraphNode          pinOut;

        public GraphPanel ()
        {
            super (new GraphLayout ());
            layout = (GraphLayout) getLayout ();

            ToolTipManager.sharedInstance ().registerComponent (this);

            mouseListener = new GraphMouseListener ();
            addMouseListener (mouseListener);
            addMouseMotionListener (mouseListener);
            addMouseWheelListener (mouseListener);

            // Arrow menu

            JMenuItem itemArrowNone = new JMenuItem (GraphEdge.iconFor (""));
            itemArrowNone.setActionCommand ("");
            itemArrowNone.addActionListener (listenerArrow);

            JMenuItem itemArrowPlain = new JMenuItem (GraphEdge.iconFor ("arrow"));
            itemArrowPlain.setActionCommand ("arrow");
            itemArrowPlain.addActionListener (listenerArrow);

            JMenuItem itemArrowCircle = new JMenuItem (GraphEdge.iconFor ("circle"));
            itemArrowCircle.setActionCommand ("circle");
            itemArrowCircle.addActionListener (listenerArrow);

            JMenuItem itemArrowCircleFill = new JMenuItem (GraphEdge.iconFor ("circleFill"));
            itemArrowCircleFill.setActionCommand ("circleFill");
            itemArrowCircleFill.addActionListener (listenerArrow);

            JMenuItem itemStraight = new JMenuItem (ImageUtil.getImage ("straight.png"));
            itemStraight.setActionCommand ("straight");
            itemStraight.addActionListener (listenerArrow);

            arrowMenu = new JPopupMenu ();
            arrowMenu.add (itemArrowNone);
            arrowMenu.add (itemArrowPlain);
            arrowMenu.add (itemArrowCircle);
            arrowMenu.add (itemArrowCircleFill);
            arrowMenu.addSeparator ();
            arrowMenu.add (itemStraight);

            // Pin menu

            JMenuItem itemPinColor = new JMenuItem ("Color");
            itemPinColor.setActionCommand ("Color");
            itemPinColor.addActionListener (listenerPin);

            JMenuItem itemPinNotes = new JMenuItem ("Notes");
            itemPinNotes.setActionCommand ("Notes");
            itemPinNotes.addActionListener (listenerPin);

            JMenuItem itemPinName = new JMenuItem ("Name");
            itemPinName.setActionCommand ("Name");
            itemPinName.addActionListener (listenerPin);

            JMenuItem itemPinDelete = new JMenuItem ("Delete");
            itemPinDelete.setActionCommand ("Delete");
            itemPinDelete.addActionListener (listenerPin);

            pinMenu = new JPopupMenu ();
            pinMenu.add (itemPinColor);
            pinMenu.add (itemPinNotes);
            pinMenu.add (itemPinName);
            pinMenu.addSeparator ();
            pinMenu.add (itemPinDelete);
        }

        public void updateUI ()
        {
            super.updateUI ();
            if (layout != null) layout.UIupdated = true;
        }

        public boolean isOptimizedDrawingEnabled ()
        {
            // Because parts can overlap, we must return false.
            return false;
        }

        public String getToolTipText (MouseEvent me)
        {
            Point p = me.getPoint ();
            GraphNode gn = findNodeAt (p, true);
            if (gn == null) return null;
            String pinName = gn.findPinAt (p);
            if (pinName.isEmpty ()) return null;

            String notes;
            String[] pieces = pinName.split ("\\.");
            if (pieces[0].equals ("in")) notes = gn.node.pinIn.get (pieces[1], "notes");
            else                         notes = gn.node.pinOut.get (pieces[1], "notes");
            if (notes.isEmpty ()) return null;

            FontMetrics fm = getFontMetrics (getFont ());
            return NodeBase.formatToolTipText (notes, fm);
        }

        public void clear ()
        {
            clearParts ();
            layout.bounds = new Rectangle ();
            offset = new Point ();
            vp.setViewPosition (new Point ());
        }

        public void clearParts ()
        {
            // Disconnect graph nodes from tree nodes
            for (Component c : getComponents ())
            {
                if (! (c instanceof GraphNode)) continue;
                GraphNode gn = (GraphNode) c;
                if (gn.panelEquationTree != null) gn.panelEquationTree.clear ();
                gn.node.graph = null;
                gn.node.fakeRoot (false);
            }

            // Flush all data
            removeAll ();
            pinIn = null;  // Don't care about the fake NodePart attached to these graph nodes.
            pinOut = null;
            edges.clear ();
        }

        public void load ()
        {
            Enumeration<?> children = container.part.children ();
            List<GraphNode> needLayout = new ArrayList<GraphNode> ();
            while (children.hasMoreElements ())
            {
                Object c = children.nextElement ();
                if (c instanceof NodePart)
                {
                    GraphNode gn = new GraphNode (this, (NodePart) c, null);
                    if (gn.open) add (gn, 0);  // Put open nodes at top of z order
                    else         add (gn);
                    if (gn.getX () == 0  &&  gn.getY () == 0) needLayout.add (gn);
                }
            }

            if (! needLayout.isEmpty ())
            {
                // TODO: use potential-field method, such as "Drawing Graphs Nicely Using Simulated Annealing" by Davidson & Harel (1996).

                // For now, a very simple layout. Arrange in a grid with some space between nodes.
                int columns = (int) Math.sqrt (needLayout.size ());  // Truncate, so more rows than columns.
                final int xgap = 100;
                final int ygap = 60;
                int x = 0;
                int y = 0;
                int h = 0;
                for (int i = 0; i < needLayout.size (); i++)
                {
                    GraphNode gn = needLayout.get (i);
                    if (i % columns == 0)
                    {
                        x = xgap;
                        y += h + ygap;
                        h = 0;
                    }
                    gn.setLocation (x, y);
                    Rectangle bounds = gn.getBounds ();
                    layout.bounds = layout.bounds.union (bounds);
                    // Don't save bounds in metadata. Only touch part if user manually adjusts layout.
                    x += bounds.width + xgap;
                    h = Math.max (h, bounds.height);
                }
            }

            if (container.part.pinIn != null  ||  container.part.pinOut != null)
            {
                Rectangle tightBounds = new Rectangle (0, 0, -1, -1);  // Needed for placing pins. layout.bounds includes (0,0) so it is not tight enough a fit round the components.
                for (Component c : getComponents ()) tightBounds = tightBounds.union (c.getBounds ());
                int y = tightBounds.y + tightBounds.height / 2;

                if (container.part.pinIn != null)
                {
                    pinIn = new GraphNode (this, null, "in");
                    if (pinIn.getX () == 0  &&  pinIn.getY () == 0)
                    {
                        Dimension d = pinIn.getPreferredSize ();
                        int x = tightBounds.x - 100 - pinIn.pinOutBounds.width - d.width;
                        pinIn.setLocation (x, y - d.height / 2);
                    }
                    add (pinIn);
                }

                if (container.part.pinOut != null)
                {
                    pinOut = new GraphNode (this, null, "out");
                    if (pinOut.getX () == 0  &&  pinOut.getY () == 0)
                    {
                        int x = tightBounds.x + tightBounds.width + 100 + pinOut.pinInBounds.width;
                        Dimension d = pinOut.getPreferredSize ();
                        pinOut.setLocation (x, y - d.height / 2);
                    }
                    add (pinOut);
                }
            }

            buildEdges ();
            validate ();  // Runs layout, so negative focus locations can work, or so that origin (0,0) is meaningful.
        }

        /**
            Scans children to set up connections.
            Assumes that all edge collections are empty.
        **/
        public void buildEdges ()
        {
            for (Component c : getComponents ())
            {
                if (! (c instanceof GraphNode)) continue;
                GraphNode gn = (GraphNode) c;

                // Build connection edges
                if (gn.node.connectionBindings == null)  // Population. Might be exposed as a pin, in which case there should be an edge to the IO block.
                {
                    MNode pin = gn.node.source.child ("$metadata", "gui", "pin");
                    if (pin != null  &&  (! pin.get ().isEmpty ()  ||  pin.child ("in") == null  &&  pin.child ("out") == null))  // This is an output population.
                    {
                        String pinName = pin.getOrDefault (gn.node.source.key ());
                        GraphEdge ge = new GraphEdge (gn, pinOut, "", pinName);
                        edges.add (ge);
                        gn.edgesIn.add (ge);
                        pinOut.edgesIn.add (ge);
                        ge.updateShape (false);
                    }
                }
                else  // Connection edges
                {
                    for (Entry<String,NodePart> e : gn.node.connectionBindings.entrySet ())
                    {
                        NodePart np = e.getValue ();
                        GraphEdge ge = new GraphEdge (gn, np, e.getKey ());
                        edges.add (ge);
                        gn.edgesOut.add (ge);
                        if (ge.nodeTo != null) ge.nodeTo.edgesIn.add (ge);
                    }
                    if (gn.edgesOut.size () == 2)
                    {
                        GraphEdge A = gn.edgesOut.get (0);  // Not necessarily same as endpoint variable named "A" in part.
                        GraphEdge B = gn.edgesOut.get (1);
                        A.edgeOther = B;
                        B.edgeOther = A;
                    }
                    for (GraphEdge ge : gn.edgesOut)
                    {
                        ge.updateShape (false);
                        if (ge.bounds != null) layout.bounds = layout.bounds.union (ge.bounds);
                    }
                }

                // Build transit edges
                if (gn.node.transitConnections != null)
                {
                    for (NodePart np : gn.node.transitConnections)
                    {
                        GraphEdge ge = new GraphEdge (gn, np, "");
                        edges.add (ge);
                        gn.edgesIn.add (ge);
                        np.graph.edgesIn.add (ge);
                        ge.updateShape (false);
                    }
                }

                // Build pin edges
                if (gn.node.pinIn != null)
                {
                    for (MNode pin : gn.node.pinIn)
                    {
                        String bindPin = pin.get ("bind", "pin");
                        if (bindPin.isEmpty ()) continue;

                        // Find peer part
                        GraphNode peer = null;
                        String bind = pin.get ("bind");
                        if (bind.isEmpty ())  // signifies a link to input block
                        {
                            if (pinIn == null) continue;
                            peer = pinIn;
                        }
                        else  // regular node
                        {
                            NodeBase nb = container.part.child (bind);
                            if (! (nb instanceof NodePart)) continue;  // could be null (not found) or a variable
                            peer = ((NodePart) nb).graph;
                        }
                        if (peer.node.pinOut == null  ||  peer.node.pinOut.child (bindPin) == null) continue;

                        // Create edge
                        GraphEdge ge = new GraphEdge (peer, gn, "in", pin.key ());
                        gn.edgesIn.add (ge);
                        peer.edgesIn.add (ge);  // Treat as an incoming edge on both sides of the link.
                        edges.add (ge);
                        ge.updateShape (false);
                    }
                }
            }
            revalidate ();
        }

        public void rebuildEdges ()
        {
            for (Component c : getComponents ())
            {
                if (! (c instanceof GraphNode)) continue;
                GraphNode gn = (GraphNode) c;
                gn.edgesIn.clear ();
                gn.edgesOut.clear ();
            }
            for (GraphEdge e : edges) e.clearBound ();
            edges.clear ();
            buildEdges ();
        }

        public void updatePins ()
        {
            // Since NodePart.updatePins() rebuilds NodePart.pinIn and pinOut, our references to them are
            // no longer valid. Need to re-copy.

            if (container.part.pinIn == null)
            {
                if (pinIn != null)
                {
                    remove (pinIn);  // Assume that rebuildEdges() will be called after this, so no need to remove dangling edges.
                    pinIn = null;
                }
            }
            else
            {
                if (pinIn == null)
                {
                    pinIn = new GraphNode (this, null, "in");
                    add (pinIn);
                }
                else
                {
                    pinIn.node.pinOut      = container.part.pinIn;
                    pinIn.node.pinOutOrder = container.part.pinInOrder;
                }
            }

            if (container.part.pinOut == null)
            {
                if (pinOut != null)
                {
                    remove (pinOut);
                    pinOut = null;
                }
            }
            else
            {
                if (pinOut == null)
                {
                    pinOut = new GraphNode (this, null, "out");
                    add (pinOut);
                }
                else
                {
                    pinOut.node.pinIn      = container.part.pinOut;
                    pinOut.node.pinInOrder = container.part.pinOutOrder;
                }
            }

            // Rebuild bounds around pin blocks.
            for (Component c : getComponents ())
            {
                if (c instanceof GraphNode) ((GraphNode) c).updatePins ();
            }

            // Update IO block layout

            Rectangle tightBounds = new Rectangle (0, 0, -1, -1);
            for (Component c : getComponents ())
                if (c != pinIn  &&  c != pinOut)
                    tightBounds = tightBounds.union (c.getBounds ());
            int y = tightBounds.y + tightBounds.height / 2;

            if (pinIn != null)
            {
                Dimension d = pinIn.getPreferredSize ();
                MNode bounds = container.part.source.child ("$metadata", "gui", "pin", "bounds", "in");
                if (bounds == null)
                {
                    int x = tightBounds.x - 100 - pinIn.pinOutBounds.width - d.width;
                    pinIn.setLocation (x, y - d.height / 2);
                }
                else  // Make the fairly safe assumption that x and y are both set to something meaningful.
                {
                    Point location = new Point ();
                    location.x = bounds.getInt ("x") + offset.x;
                    location.y = bounds.getInt ("y") + offset.y;
                    pinIn.setLocation (location);
                }
                pinIn.setSize (d);  // Only necessary for previously-existing node, but we don't bother remembering whether it is new or existing.
                layout.bounds = layout.bounds.union (pinIn.getBounds ());
                pinIn.title.updateSelected ();  // In case override status changed.
            }

            if (pinOut != null)
            {
                Dimension d = pinOut.getPreferredSize ();
                MNode bounds = container.part.source.child ("$metadata", "gui", "pin", "bounds", "out");
                if (bounds == null)
                {
                    int x = tightBounds.x + tightBounds.width + 100 + pinOut.pinInBounds.width;
                    pinOut.setLocation (x, y - d.height / 2);
                }
                else
                {
                    Point location = new Point ();
                    location.x = bounds.getInt ("x") + offset.x;
                    location.y = bounds.getInt ("y") + offset.y;
                    pinOut.setLocation (location);
                }
                pinOut.setSize (d);
                layout.bounds = layout.bounds.union (pinOut.getBounds ());
                pinOut.title.updateSelected ();
            }
        }

        /**
            Add a node to an existing graph.
            Must always be followed by a call to rebuildEdges() to update connections.
            These functions are separated to simplify code in undo objects.
        **/
        public void addPart (NodePart node)
        {
            GraphNode gn = new GraphNode (this, node, null);
            add (gn, 0);  // put at top of z-order, so user can find it easily
            layout.bounds = layout.bounds.union (gn.getBounds ());
            revalidate ();
        }

        /**
            Remove node from an existing graph.
            Must always be followed by a call to rebuildEdges() to update connections.
            These functions are separated to simplify code in undo objects.
        **/
        public void removePart (NodePart node)
        {
            Rectangle bounds = node.graph.getBounds ();
            remove (node.graph);
            node.graph = null;
            revalidate ();
            repaint (bounds);
        }

        public void updateLock ()
        {
            for (Component c : getComponents ())
            {
                if (c instanceof GraphNode) ((GraphNode) c).panelEquationTree.updateLock ();
            }
        }

        public void updateFilterLevel ()
        {
            for (Component c : getComponents ())
            {
                if (c instanceof GraphNode) ((GraphNode) c).panelEquationTree.updateFilterLevel ();
            }
        }

        public void clearSelection ()
        {
            for (Component c : getComponents ())
            {
                if (c instanceof GraphNode) ((GraphNode) c).setSelected (false);
            }
        }

        public List<GraphNode> getSelection ()
        {
            List<GraphNode> result = new ArrayList<GraphNode> ();
            for (Component c : getComponents ())
            {
                if (! (c instanceof GraphNode)) continue;
                GraphNode g = (GraphNode) c;
                if (g.selected) result.add (g);
            }
            return result;
        }

        public GraphEdge findTipAt (Point p)
        {
            Vector2 p2 = new Vector2 (p.x, p.y);
            for (GraphEdge e : edges)
            {
                if (e.tip  != null  &&  e.tip.distance (p2) < GraphEdge.arrowheadLength) return e;
                // These tests extend the clickable area to include the full width of the pin zone.
                if (e.pinKeyFrom != null  &&  findTipAtPin (p, e.nodeFrom, e.pinSideFrom, e.pinKeyFrom)) return e;
                if (e.pinKeyTo   != null  &&  findTipAtPin (p, e.nodeTo,   e.pinSideTo,   e.pinKeyTo  )) return e;
            }
            return null;
        }

        /**
            Subroutine of findTipAt()
        **/
        public boolean findTipAtPin (Point p, GraphNode g, String pinSide, String pinKey)
        {
            if (pinSide.equals ("in"))
            {
                if (g.pinInBounds != null  &&  g.pinInBounds.contains (p))
                {
                    MNode pin = g.node.pinIn.child (pinKey);
                    if (pin != null)
                    {
                        int lineHeight = g.pinInBounds.height / g.node.pinIn.size ();
                        int y = p.y - g.pinInBounds.y;
                        if (pin.getInt ("order") == y / lineHeight) return true;
                    }
                }
            }
            else  // pinSide is "out"
            {
                if (g.pinOutBounds != null  &&  g.pinOutBounds.contains (p))
                {
                    MNode pin = g.node.pinOut.child (pinKey);
                    if (pin != null)
                    {
                        int lineHeight = g.pinOutBounds.height / g.node.pinOut.size ();
                        int y = p.y - g.pinOutBounds.y;
                        if (pin.getInt ("order") == y / lineHeight) return true;
                    }
                }
            }
            return false;
        }

        public GraphNode findNodeAt (Point p, boolean includePins)
        {
            for (Component c : getComponents ())
            {
                if (! (c instanceof GraphNode)) continue;
                GraphNode g = (GraphNode) c;
                Rectangle bounds = g.getBounds ();
                if (bounds.contains (p)) return g;
                if (! includePins) continue;
                if (g.pinInBounds  != null  &&  g.pinInBounds .contains (p)) return g;
                if (g.pinOutBounds != null  &&  g.pinOutBounds.contains (p)) return g;
            }
            return null;
        }

        public List<GraphNode> findNodesIn (Rectangle r)
        {
            List<GraphNode> result = new ArrayList<GraphNode> ();
            for (Component c : getComponents ())
            {
                if (c instanceof GraphNode  &&  r.intersects (c.getBounds ())) result.add ((GraphNode) c);
            }
            return result;
        }

        public GraphNode findNodeClosest (Point p)
        {
            List<GraphNode> nodes = new ArrayList<GraphNode> ();
            for (Component c : getComponents ()) if (c instanceof GraphNode) nodes.add ((GraphNode) c);
            return findNodeClosest (p, nodes);
        }

        public GraphNode findNodeClosest (Point p, List<GraphNode> nodes)
        {
            GraphNode result = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (GraphNode g : nodes)
            {
                if (g == pinIn  ||  g == pinOut) continue;
                double d = p.distance (g.getLocation ());
                if (d < bestDistance)
                {
                    result = g;
                    bestDistance = d;
                }
            }
            return result;
        }

        public GraphNode findNode (String name)
        {
            for (Component c : getComponents ())
            {
                if (! (c instanceof GraphNode)) continue;
                GraphNode gn = (GraphNode) c;
                if (gn.node.source.key ().equals (name)  &&  gn != pinIn  &&  gn != pinOut) return gn;
            }
            return null;
        }

        public void paintComponent (Graphics g)
        {
            // This basically does nothing, since ui is (usually) null. Despite being opaque, our background comes from our container.
            super.paintComponent (g);

            // Fill background
            Graphics2D g2 = (Graphics2D) g.create ();
            g2.setColor (background);
            Rectangle clip = g2.getClipBounds ();
            g2.fillRect (clip.x, clip.y, clip.width, clip.height);

            // Draw selection region
            if (mouseListener.selectStart != null)
            {
                g2.setColor (new Color (0x100000FF, true));  // TODO: base this color on current L&F
                g2.fill (mouseListener.selectRegion);
            }

            // Draw connection edges
            Stroke oldStroke = g2.getStroke ();
            g2.setStroke (new BasicStroke (GraphEdge.strokeThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (GraphEdge e : edges) if (e.bounds.intersects (clip)) e.paintComponent (g2);

            // Draw pins
            g2.setStroke (oldStroke);
            for (Component c : getComponents ())
            {
                if (c instanceof GraphNode) ((GraphNode) c).paintPins (g2, clip);  // Test clip bounds against pins. Paint pin if any overlap.
            }

            g2.dispose ();
        }

        ActionListener listenerArrow = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                NodeBase n = arrowEdge.nodeFrom.node.child (arrowEdge.alias);
                MNode metadata = new MVolatile ();
                String action = e.getActionCommand ();
                if (action.equals ("straight"))
                {
                    if (n.source.getFlag ("$metadata", "gui", "arrow", "straight")) metadata.set ("0", "gui", "arrow", "straight");
                    else                                                            metadata.set ("",  "gui", "arrow", "straight");
                }
                else
                {
                    metadata.set (action, "gui", "arrow");
                }
                MainFrame.instance.undoManager.apply (new ChangeAnnotations (n, metadata));
            }
        };

        ActionListener listenerPin = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                GraphNode g = (GraphNode) pinMenu.getInvoker ();
                String pinSide;
                String pinKey;
                Point location;
                if (g == graphPanel.pinIn)
                {
                    int lineHeight = g.pinOutBounds.height / container.part.pinIn.size ();
                    int y = graphPanel.popupLocation.y - GraphNode.border.t;
                    int index = y / lineHeight;
                    pinKey = container.part.pinInOrder.get (index).key ();
                    pinSide = "in";
                    location = new Point (g.pinOutBounds.x + lineHeight / 2, g.pinOutBounds.y + index * lineHeight);
                }
                else  // g is pinOut
                {
                    int lineHeight = g.pinInBounds.height / container.part.pinOut.size ();
                    int y = graphPanel.popupLocation.y - GraphNode.border.t;
                    int index = y / lineHeight;
                    pinKey = container.part.pinOutOrder.get (index).key ();
                    pinSide = "out";
                    location = new Point (g.pinInBounds.x, g.pinInBounds.y + index * lineHeight);
                }

                UndoManager um = MainFrame.instance.undoManager;
                switch (e.getActionCommand ())
                {
                    case "Color":
                    {
                        Color initialColor = Color.black;
                        String colorName = container.part.source.get ("$metadata", "gui", "pin", pinSide, pinKey, "color");
                        if (! colorName.isEmpty ())
                        {
                            try {initialColor = Color.decode (colorName);}
                            catch (NumberFormatException error) {}
                        }

                        Color chosenColor = JColorChooser.showDialog (MainFrame.instance, "", initialColor);
                        if (chosenColor != null  &&  ! chosenColor.equals (initialColor))
                        {
                            colorName = "#" + Integer.toHexString (chosenColor.getRGB () & 0xFFFFFF);
                            MNode metadata = new MVolatile ();
                            metadata.set (colorName, "gui", "pin", pinSide, pinKey, "color");
                            um.apply (new ChangeAnnotations (container.part, metadata));
                        }
                        break;
                    }
                    case "Notes":
                    {
                        container.switchFocus (false, false);  // Ensure that parent node is showing.
                        NodeAnnotation notes = AddAnnotation.findOrCreate (um, container.part, "gui", "pin", pinSide, pinKey, "notes");
                        JTree tree = container.getParentEquationTree ().tree;
                        tree.startEditingAtPath (new TreePath (notes.getPath ()));
                        break;
                    }
                    case "Name":
                    {
                        // Show dialog to get new name.
                        JTextField editor = new NTextField (pinKey, Math.max (10, pinKey.length ()));

                        ActionMap actionMap = editor.getActionMap ();
                        actionMap.put ("Cancel", new AbstractAction ("Cancel")
                        {
                            public void actionPerformed (ActionEvent evt)
                            {
                                graphPanel.remove (editor);
                                graphPanel.repaint (editor.getBounds ());
                            }
                        });

                        editor.addActionListener (new ActionListener ()
                        {
                            public void actionPerformed (ActionEvent e)
                            {
                                graphPanel.remove (editor);
                                graphPanel.repaint (editor.getBounds ());
                                applyPinNameChange (pinSide, pinKey, editor.getText ());
                            }
                        });

                        editor.addFocusListener (new FocusListener ()
                        {
                            public void focusGained (FocusEvent e)
                            {
                            }

                            public void focusLost (FocusEvent e)
                            {
                                if (editor.getParent () == graphPanel)
                                {
                                    graphPanel.remove (editor);
                                    graphPanel.repaint (editor.getBounds ());
                                    applyPinNameChange (pinSide, pinKey, editor.getText ());
                                }
                            }
                        });

                        editor.setLocation (location);
                        graphPanel.add (editor);
                        graphPanel.setComponentZOrder (editor, 0);
                        editor.requestFocusInWindow ();
                        break;
                    }
                    case "Delete":
                    {
                        um.addEdit (new CompoundEdit ());
                        GraphMouseListener gml = graphPanel.mouseListener;
                        if (pinSide.equals ("in"))
                        {
                            // Remove pin annotations from subscribers, as this info is one thing
                            // that sustains the existence of a pin.
                            MNode subscribers = container.part.pinIn.child (pinKey, "part");
                            if (subscribers != null)
                            {
                                for (MNode p : subscribers)
                                {
                                    // Retrieve the GUI node.
                                    NodePart part = (NodePart) container.part.child (p.key ());
                                    if (part.connectionBindings == null)  // Regular part, so this is an exported inner pin.
                                    {
                                        // Find the originating pin. Could be more than one.
                                        for (MNode q : part.pinIn)
                                        {
                                            if (! q.get ("bind", "pin").equals (pinKey)) continue;
                                            if (! q.get ("bind").isEmpty ()) continue;

                                            String pinFrom = q.key ();
                                            if (! gml.removeAuto (um, part, pinFrom))
                                            {
                                                MNode metadata = new MVolatile ();
                                                gml.clearBinding (metadata, pinFrom);
                                                um.apply (new ChangeAnnotations (part, metadata));
                                            }
                                        }
                                    }
                                    else   // Connection, so this is an exposure.
                                    {
                                        DeleteAnnotation da = DeleteAnnotation.withName (part, "gui", "pin");
                                        if (da != null) um.apply (da);
                                    }
                                }
                            }

                            // Remove pin annotations from container itself.
                            DeleteAnnotation da = DeleteAnnotation.withName (container.part, "gui", "pin", "in", pinKey);
                            if (da != null) um.apply (da);
                        }
                        else  // pinSide is "out"
                        {
                            MNode subscribers = container.part.pinOut.child (pinKey, "part");
                            if (subscribers != null)
                            {
                                for (MNode p : subscribers)
                                {
                                    NodePart part = (NodePart) container.part.child (p.key ());
                                    if (part.connectionBindings == null)
                                    {
                                        DeleteAnnotation da = DeleteAnnotation.withName (part, "gui", "pin");
                                        if (da != null) um.apply (da);
                                    }
                                    else
                                    {
                                        DeleteAnnotation da = DeleteAnnotation.withName (part, "gui", "pin", "pass");
                                        if (da != null) um.apply (da);
                                    }
                                }
                            }

                            DeleteAnnotation da = DeleteAnnotation.withName (container.part, "gui", "pin", "out", pinKey);
                            if (da != null) um.apply (da);
                        }
                        um.endCompoundEdit ();
                        break;
                    }
                }
            }
        };

        public void applyPinNameChange (String pinSide, String pinKey, String newKey)
        {
            // Verify that name does not conflict
            if (newKey.isEmpty ()) return;
            MNode pinData;
            if (pinSide.equals ("in")) pinData = container.part.pinIn;
            else                       pinData = container.part.pinOut;
            for (MNode p : pinData) if (newKey.equals (p.key ())) return;   // Abort change if any key matches. If the name has not changed, this includes its own value.

            // Apply change
            UndoManager um = MainFrame.instance.undoManager;
            um.addEdit (new CompoundEdit ());
            if (pinSide.equals ("in"))
            {
                // Rename in subscribers, since they can cause the pin to exist.
                MNode subscribers = container.part.pinIn.child (pinKey, "part");
                if (subscribers != null)
                {
                    for (MNode p : subscribers)
                    {
                        NodePart part = (NodePart) container.part.child (p.key ());
                        if (part.connectionBindings == null)  // Regular part, so this is an exported inner pin.
                        {
                            // Find the originating pin. Could be more than one.
                            for (MNode q : part.pinIn)
                            {
                                if (! q.get ("bind", "pin").equals (pinKey)) continue;
                                if (! q.get ("bind").isEmpty ()) continue;

                                String pinFrom = q.key ();
                                MNode metadata = new MVolatile ();
                                metadata.set (newKey, "gui", "pin", "in", pinFrom, "bind", "pin");
                                um.apply (new ChangeAnnotations (part, metadata));
                            }
                        }
                        else   // Connection, so this is an exposure.
                        {
                            MNode metadata = new MVolatile ();
                            metadata.set (newKey, "gui", "pin");
                            um.apply (new ChangeAnnotations (part, metadata));
                        }
                    }
                }

                // Rename pin annotations in container itself.
                MNode child = container.part.source.child ("$metadata", "gui", "pin", "in", pinKey);
                if (child != null)  // There actually is a container-level pin annotation.
                {
                    // Ideally we would use MNode.move(), but that would require a new kind of undo class.
                    // Instead, use edit actions that are on hand. A move is equivalent to a delete plus a create.

                    MNode metadata = new MVolatile ();
                    metadata.set (child, "gui", "pin", "in", newKey);

                    DeleteAnnotation da = DeleteAnnotation.withName (container.part, "gui", "pin", "in", pinKey);
                    if (da != null) um.apply (da);

                    um.apply (new ChangeAnnotations (container.part, metadata));
                }
            }
            else  // pinSide is "out"
            {
                MNode subscribers = container.part.pinOut.child (pinKey, "part");
                if (subscribers != null)
                {
                    for (MNode p : subscribers)
                    {
                        NodePart part = (NodePart) container.part.child (p.key ());
                        MNode metadata = new MVolatile ();
                        if (part.connectionBindings == null)
                        {
                            metadata.set (newKey, "gui", "pin");
                        }
                        else
                        {
                            metadata.set (newKey, "gui", "pin", "pass");
                        }
                        um.apply (new ChangeAnnotations (part, metadata));
                    }
                }

                MNode child = container.part.source.child ("$metadata", "gui", "pin", "out", pinKey);
                if (child != null)
                {
                    MNode metadata = new MVolatile ();
                    metadata.set (child, "gui", "pin", "out", newKey);

                    DeleteAnnotation da = DeleteAnnotation.withName (container.part, "gui", "pin", "out", pinKey);
                    if (da != null) um.apply (da);

                    um.apply (new ChangeAnnotations (container.part, metadata));
                }
            }
            um.endCompoundEdit ();
        }
    }

    public class GraphLayout implements LayoutManager2
    {
        public Rectangle bounds = new Rectangle ();  // anchored at (0,0)
        public boolean   UIupdated;

        public void addLayoutComponent (String name, Component comp)
        {
            addLayoutComponent (comp, name);
        }

        public void addLayoutComponent (Component comp, Object constraints)
        {
            Dimension d = comp.getPreferredSize ();
            comp.setSize (d);
            Point p = comp.getLocation ();
            bounds = bounds.union (new Rectangle (p, d));
        }

        public void removeLayoutComponent (Component comp)
        {
        }

        public Dimension preferredLayoutSize (Container target)
        {
            return bounds.getSize ();
        }

        public Dimension minimumLayoutSize (Container target)
        {
            return preferredLayoutSize (target);
        }

        public Dimension maximumLayoutSize (Container target)
        {
            return preferredLayoutSize (target);
        }

        public float getLayoutAlignmentX (Container target)
        {
            return 0;
        }

        public float getLayoutAlignmentY (Container target)
        {
            return 0;
        }

        public void invalidateLayout (Container target)
        {
        }

        public void layoutContainer (Container target)
        {
            // Only change layout if a component has moved into negative space.
            if (bounds.x >= 0  &&  bounds.y >= 0)
            {
                if (UIupdated)
                {
                    UIupdated = false;
                    for (Component c : graphPanel.getComponents ())
                    {
                        c.setSize (c.getPreferredSize ());
                    }
                    for (GraphEdge ge : graphPanel.edges)
                    {
                        ge.updateShape (false);
                    }
                }
                return;
            }

            shiftViewport (vp.getViewPosition ());  // Neutral shift. Side effect is to update all components with positive coordinates.
        }

        /**
            Moves viewport so that the given point is in the upper-left corner of the visible area.
            Does a tight fit around existing components to minimize size of scrolled region.
            This could result in a shift of components, even if the requested position is same as current position.
            @param n New position of viewport, in terms of current viewport layout.
            @return Amount to shift any external components to keep position relative to internal components.
            This value should be added to their coordinates.
        **/
        public Point shiftViewport (Point n)
        {
            // Compute tight bounds which include new position
            bounds = new Rectangle (n.x, n.y, 1, 1);
            for (Component c : graphPanel.getComponents ())
            {
                bounds = bounds.union (c.getBounds ());
            }
            for (GraphEdge ge : graphPanel.edges)
            {
                if (ge.nameTo != null) continue;  // Don't include external edges in tight bound. Their size is arbitrary and viewport-dependent.
                bounds = bounds.union (ge.bounds);
            }

            // Shift components so bounds start at origin
            Point d = new Point (-bounds.x, -bounds.y);
            bounds.translate (d.x, d.y);
            graphPanel.offset.translate (d.x, d.y);
            n.translate (d.x, d.y);
            vp.setViewPosition (n);  // Need to do this before GraphEdge.updateShape().

            if (d.x != 0  ||  d.y != 0)  // Avoid calling these expensive operations unless shift actually occurred.
            {
                for (Component c : graphPanel.getComponents ())
                {
                    Point p = c.getLocation ();
                    p.translate (d.x, d.y);
                    c.setLocation (p);
                }
                for (GraphEdge ge : graphPanel.edges)
                {
                    ge.updateShape (false);
                }
            }

            return d;
        }

        public void componentMoved (Component comp)
        {
            componentMoved (comp.getBounds ());
        }

        public void componentMoved (Rectangle next)
        {
            Rectangle old = bounds;
            bounds = bounds.union (next);
            if (! bounds.equals (old)) graphPanel.revalidate ();
        }
    }

    public class GraphTransferHandler extends TransferHandler
    {
        public boolean canImport (TransferSupport xfer)
        {
            return container.transferHandler.canImport (xfer);
        }

        public boolean importData (TransferSupport xfer)
        {
            return container.transferHandler.importData (xfer);
        }

        public void exportDone (JComponent source, Transferable data, int action)
        {
            MainFrame.instance.undoManager.endCompoundEdit ();
        }
    }

    public class GraphMouseListener extends MouseInputAdapter implements ActionListener
    {
        Point      startPan;
        GraphEdge  edge;
        String     orderPin;
        Point      selectStart;
        Rectangle  selectRegion;
        MouseEvent lastEvent;
        Timer      timer = new Timer (100, this);

        public void mouseClicked (MouseEvent me)
        {
            if (SwingUtilities.isLeftMouseButton (me)  &&  me.getClickCount () == 2)
            {
                container.drillUp ();
            }
        }

        public void mouseWheelMoved (MouseWheelEvent e)
        {
            if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
            {
                Point p = vp.getViewPosition ();  // should be exactly same as current scrollbar values
                if (e.isShiftDown ()) p.x += e.getUnitsToScroll () * 15;  // 15px is approximately one line of text; units to scroll is typically 3, so about 45px or 3 lines of text per click of scroll wheel
                else                  p.y += e.getUnitsToScroll () * 15;
                graphPanel.layout.shiftViewport (p);
                graphPanel.revalidate ();  // necessary to show scrollbars when components go past right or bottom
                graphPanel.repaint ();
            }
        }

        public void mouseMoved (MouseEvent me)
        {
            if (container.locked) return;
            GraphEdge e = graphPanel.findTipAt (me.getPoint ());
            if (e == null) setCursor (Cursor.getDefaultCursor ());
            else           setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
        }

        public void mousePressed (MouseEvent me)
        {
            Point p = me.getPoint ();

            if (SwingUtilities.isRightMouseButton (me)  ||  me.isControlDown ())  // Context menus
            {
                if (container.locked) return;

                GraphNode g = graphPanel.findNodeAt (p, true);  // Only a click in the pin zone will return non-null here. If it were in the graph node proper, the click would have been routed there instead.
                if (g != null  &&  (g == graphPanel.pinIn  ||  g == graphPanel.pinOut))
                {
                    Point q = g.getLocation ();
                    p.x -= q.x;
                    p.y -= q.y;
                    graphPanel.popupLocation = p;
                    graphPanel.pinMenu.show (g, p.x, p.y);
                }
                else
                {
                    GraphEdge e = graphPanel.findTipAt (p);
                    if (e != null  &&  e.pinKeyFrom != null) e = null;  // Don't show arrow menu for pin-to-pin links.
                    if (e == null)
                    {
                        container.getTitleFocus ().requestFocusInWindow ();
                        graphPanel.popupLocation = p;
                        container.menuPopup.show (graphPanel, p.x, p.y);
                    }
                    else
                    {
                        graphPanel.arrowEdge = e;
                        graphPanel.arrowMenu.show (graphPanel, p.x, p.y);
                    }
                }
            }
            else if (SwingUtilities.isLeftMouseButton (me))
            {
                if (container.locked) return;

                // Probe for edges and pins.
                edge = null;
                GraphNode g = null;
                boolean shift = me.isShiftDown ();
                if (shift)  // When shift is down, prioritize pin over edge.
                {
                    g = graphPanel.findNodeAt (p, true);  // Only a click in the pin zone will return non-null here. If it were in the graph node proper, the click would have been routed there instead.
                    if (g == null) edge = graphPanel.findTipAt (p);
                }
                else  // Otherwise, prioritize edge over pin.
                {
                    edge = graphPanel.findTipAt (p);
                    if (edge == null) g = graphPanel.findNodeAt (p, true);
                }

                // Prepare for dragging.
                if (edge == null)
                {
                    // Check if a pin region is under the click.
                    if (g == null)  // Bare background --> do selection
                    {
                        selectStart = p;
                        selectRegion = new Rectangle (p);
                    }
                    else  // In pin zone, and pin is not currently bound to an edge. If it were connected, it would have been caught by findTipAt().
                    {
                        // Determine which pin it is.
                        if (g.pinInBounds != null  &&  g.pinInBounds.contains (p))
                        {
                            int lineHeight = g.pinInBounds.height / g.node.pinIn.size ();
                            int y = p.y - g.pinInBounds.y;
                            MNode pin = g.node.pinInOrder.get (y / lineHeight);
                            if (shift  &&  g == graphPanel.pinOut) orderPin = "out." + pin.key ();
                            else                                   edge = new GraphEdge (g, null, "in", pin.key ());
                        }
                        else if (g.pinOutBounds != null  &&  g.pinOutBounds.contains (p))
                        {
                            int lineHeight = g.pinOutBounds.height / g.node.pinOut.size ();
                            int y = p.y - g.pinOutBounds.y;
                            MNode pin = g.node.pinOutOrder.get (y / lineHeight);
                            if (shift  &&  g == graphPanel.pinIn) orderPin = "in." + pin.key ();
                            else                                  edge = new GraphEdge (g, null, "out", pin.key ());
                        }
                        if (edge != null)  // Finish constructing transient edge. It will be deleted by mouseRelease().
                        {
                            //edge.anchor = p;  // Position in this component where drag started.
                            edge.tip = new Vector2 (0, 0);  // This is normally created by GraphEdge.updateShape(), but we don't call that first.
                            graphPanel.edges.add (edge);
                        }
                    }
                }
                else  // arrowhead or pin
                {
                    if (edge.pinKeyFrom != null)  // originates from a pin, so may need to reconfigure edge for dragging
                    {
                        // Which end is under the cursor?
                        Vector2 p2 = new Vector2 (p.x, p.y);
                        if (edge.root.distance (p2) < edge.tip.distance (p2))  // near root
                        {
                            // Since root is always the output pin in a completed edge, we need to reverse it.
                            // During the drag, the edge will instead originate from the input pin.
                            edge.reversePins ();
                        }
                    }
                }
                if (orderPin != null)
                {
                    setCursor (Cursor.getPredefinedCursor (Cursor.HAND_CURSOR));
                }
                else if (edge != null)
                {
                    setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    edge.animate (p);  // Activates tipDrag
                }
            }
            else if (SwingUtilities.isMiddleMouseButton (me))
            {
                startPan = p;
                setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
            }
        }

        public void mouseDragged (MouseEvent me)
        {
            if (startPan != null)
            {
                Point here = me.getPoint ();  // relative to origin of viewport, which may not be visible

                Point p = vp.getViewPosition ();  // should be exactly same as current scrollbar values
                p.x -= here.x - startPan.x;
                p.y -= here.y - startPan.y;
                Point d = graphPanel.layout.shiftViewport (p);
                startPan.x += d.x;
                startPan.y += d.y;
                graphPanel.revalidate ();  // necessary to show scrollbars when components go past right or bottom
                graphPanel.repaint ();
            }
            else if (edge != null  ||  selectStart != null)
            {
                Point pp = vp.getLocationOnScreen ();
                Point pm = me.getLocationOnScreen ();
                pm.x -= pp.x;  // relative to upper-left corner of visible region
                pm.y -= pp.y;
                Dimension extent = vp.getExtentSize ();
                Point d;  // Amount by which shiftViewport() moved existing components relative to viewport coordinates.
                boolean auto =  me == lastEvent;
                if (pm.x < 0  ||  pm.x > extent.width  ||  pm.y < 0  ||  pm.y > extent.height)  // out of bounds
                {
                    if (auto)
                    {
                        int dx = pm.x < 0 ? pm.x : (pm.x > extent.width  ? pm.x - extent.width  : 0);
                        int dy = pm.y < 0 ? pm.y : (pm.y > extent.height ? pm.y - extent.height : 0);

                        Point p = vp.getViewPosition ();
                        p.translate (dx, dy);
                        d = graphPanel.layout.shiftViewport (p);
                        me.translatePoint (dx + d.x, dy + d.y);  // Makes permanent change to lastEvent. Does not change its getLocationOnScreen()
                    }
                    else  // A regular drag
                    {
                        lastEvent = me;  // Let the user adjust speed.
                        timer.start ();
                        return;  // Don't otherwise process it.
                    }
                }
                else  // in bounds
                {
                    timer.stop ();
                    lastEvent = null;
                    if (auto) return;
                    d = new Point ();
                }

                Point here = me.getPoint ();
                if (edge != null)
                {
                    // Don't let requested coordinates go negative, or it will force another shift of viewport.
                    here.x = Math.max (here.x, 0);
                    here.y = Math.max (here.y, 0);
                    edge.animate (here);
                }
                else if (selectStart != null)
                {
                    Rectangle old = selectRegion;
                    selectStart.translate (d.x, d.y);
                    selectRegion = new Rectangle (selectStart);
                    selectRegion.add (here);
                    graphPanel.repaint (old.union (selectRegion));
                }
            }
        }

        public void mouseReleased (MouseEvent me)
        {
            startPan = null;
            lastEvent = null;
            timer.stop ();
            setCursor (Cursor.getPredefinedCursor (Cursor.DEFAULT_CURSOR));

            if (edge != null)  // Finish assigning endpoint
            {
                edge.tipDrag = false;  // For those cases where edge will continue to be used, rather than evaporate or be replaced.

                UndoManager um = MainFrame.instance.undoManager;
                um.addEdit (new CompoundEdit ());  // Everything is done inside a compound, even if it is a single edit.

                GraphNode nodeFrom = edge.nodeFrom;
                NodePart partFrom = nodeFrom.node;
                NodeVariable variable = (NodeVariable) partFrom.child (edge.alias);  // This can be null if alias is empty (in the case of a pin-to-pin link).
                Point p = me.getPoint ();
                GraphNode nodeTo = graphPanel.findNodeAt (p, true);
                if (nodeTo == null  ||  nodeTo == nodeFrom  &&  ! edge.alias.isEmpty ())  // Disconnect the edge
                {
                    if (variable == null)  // pin-to-pin
                    {
                        // Remove edge
                        if (edge.pinSideTo == null)  // transient edge that did not get completed
                        {
                            graphPanel.edges.remove (edge);
                            graphPanel.repaint (edge.bounds);
                        }
                        else  // previously-existing edge that has been disconnected
                        {
                            if (edge.nodeTo == graphPanel.pinOut)
                            {
                                if (edge.pinKeyFrom == null)  // population marked as output pin
                                {
                                    DeleteAnnotation da = null;
                                    da = DeleteAnnotation.withName (partFrom, "gui", "pin");
                                    if (da != null) um.apply (da);
                                }
                                else  // exported inner pin
                                {
                                    clearBindingOut (um, edge.pinKeyTo);
                                }
                            }
                            else if (edge.nodeFrom == graphPanel.pinOut)
                            {
                                clearBindingOut (um, edge.pinKeyFrom);
                            }
                            else
                            {
                                NodePart part;
                                String   pin;
                                if (edge.pinSideFrom.equals ("in"))
                                {
                                    part = partFrom;
                                    pin  = edge.pinKeyFrom;
                                }
                                else  // edge.pinSideFrom is "out"
                                {
                                    part = edge.nodeTo.node;
                                    pin  = edge.pinKeyTo;
                                }
                                if (! removeAuto (um, part, pin))
                                {
                                    MNode data = new MVolatile ();
                                    clearBinding (data, pin);
                                    um.apply (new ChangeAnnotations (part, data));
                                }
                            }
                        }
                    }
                    else  // regular connector, possibly bound to pin
                    {
                        // Change to disconnected state
                        disconnect (um, variable);

                        // Handle any changes in pin structure.
                        if (edge.nodeTo != null)
                        {
                            DeleteAnnotation da = null;
                            if (edge.nodeTo == graphPanel.pinOut)
                            {
                                // Kill only the output side of a pass-through connection
                                da = DeleteAnnotation.withName (partFrom, "gui", "pin", "pass");
                            }
                            else if (edge.nodeTo == graphPanel.pinIn)
                            {
                                // Stop acting as an input pin. This will also kill the output side of a pass-through connection.
                                da = DeleteAnnotation.withName (partFrom, "gui", "pin");
                            }
                            if (da != null) um.apply (da);
                        }
                    }
                }
                else if (nodeTo == edge.nodeTo)  // No change in target node
                {
                    // Usually, there is nothing to do but end the drag.
                    // However, if the target is specifically a pin, then need to update metadata.

                    boolean handled = false;
                    NodePart partTo = nodeTo.node;
                    String pinNew = nodeTo.findPinAt (p);
                    String[] pieces = pinNew.split ("\\.", 2);
                    String newSide = pieces[0];
                    String newKey  = "";
                    if (pieces.length > 1) newKey = pieces[1];

                    if (variable == null)  // from pin
                    {
                        // In general, if a gesture is forbidden, then leave the pin unchanged (as opposed to deleting it).
                        // There are several ways for that to happen in this case.
                        if (! pinNew.isEmpty ())
                        {
                            if (! newKey.equals (edge.pinKeyTo)  &&  newSide.equals (edge.pinSideTo))  // Only do something if pin changed, but don't change sides.
                            {
                                handled = true;
                                MNode data = new MVolatile ();  // For convenience, create this once. It is only used once by some case below.
                                if (edge.pinSideTo.equals ("in"))  // targeting a different input
                                {
                                    if (nodeTo == graphPanel.pinOut)
                                    {
                                        if (edge.pinKeyFrom == null)  // population marked as output pin
                                        {
                                            data.set (newKey, "gui", "pin");
                                            um.apply (new ChangeAnnotations (partFrom, data));
                                        }
                                        else  // exported output pin
                                        {
                                            clearBindingOut (um, edge.pinKeyTo);
                                            setBindingOut (data, newKey, partFrom.source.key (), edge.pinKeyFrom);
                                            um.apply (new ChangeAnnotations (container.part, data));
                                        }
                                    }
                                    else  // regular pin-to-pin, or exported input pin
                                    {
                                        String partFromName;
                                        if (nodeFrom == graphPanel.pinIn) partFromName = "";  // This is an exported input pin, so don't set part name.
                                        else                              partFromName = partFrom.source.key ();

                                        String pinName = addAuto (um, partTo, newKey);
                                        setBinding (data, pinName, partFromName, edge.pinKeyFrom);
                                        if (! removeAuto (um, partTo, edge.pinKeyTo)) clearBinding (data, edge.pinKeyTo);
                                        um.apply (new ChangeAnnotations (partTo, data));
                                    }
                                }
                                else  // edge.pinSideTo is "out" --> Selected a different output to draw from, while input pin remains the same.
                                {
                                    if (nodeFrom == graphPanel.pinOut)
                                    {
                                        setBindingOut (data, edge.pinKeyFrom, partTo.source.key (), newKey);
                                        um.apply (new ChangeAnnotations (container.part, data));
                                    }
                                    else
                                    {
                                        String partToName;
                                        if (nodeTo == graphPanel.pinIn) partToName = "";
                                        else                            partToName = partTo.source.key ();
                                        setBinding (data, edge.pinKeyFrom, partToName, newKey);
                                        um.apply (new ChangeAnnotations (partFrom, data));
                                    }
                                    // The edge would need to be un-reversed, except that we regenerate all edges in ChangeAnnotations.
                                }
                            }
                            if (! handled  &&  edge.pinSideTo.equals ("out"))  // Edge goes back to original pin. In the case where destination is "out", the edge needs to be un-reversed.
                            {
                                edge.reversePins ();
                            }
                        }
                    }
                    else  // from connection
                    {
                        // In general, there is nothing to do for connections.
                        // However, if this is a pass-through connection, then a target pin may have changed.
                        boolean pass =  nodeTo == graphPanel.pinOut;
                        if (nodeTo == graphPanel.pinIn  ||  pass)
                        {
                            String    oldKey = partFrom.source.getOrDefault (partFrom.source.key (), "$metadata", "gui", "pin");
                            if (pass) oldKey = partFrom.source.getOrDefault (oldKey,                 "$metadata", "gui", "pin", "pass");
                            if (! newKey.equals (oldKey))  // target pin has changed
                            {
                                MNode metadata = new MVolatile ();
                                if (pass) metadata.set (newKey, "gui", "pin", "pass");
                                else      metadata.set (newKey, "gui", "pin");
                                um.apply (new ChangeAnnotations (partFrom, metadata));
                                handled = true;
                            }
                        }
                    }

                    if (! handled) edge.animate (null);  // Stop drag mode and restore connection to normal.
                }
                else  // Connect to new endpoint (nodeTo != edge.nodeTo  &&  nodeTo != null)
                {
                    String pinNew = nodeTo.findPinAt (p);
                    if (variable == null)  // from pin
                    {
                        String[] pieces = pinNew.split ("\\.", 2);
                        String newSide = pieces[0];
                        String newKey  = "";
                        if (pieces.length > 1) newKey = pieces[1];

                        boolean handled = false;
                        if (nodeFrom == graphPanel.pinOut)
                        {
                            if (nodeTo != graphPanel.pinIn  &&  newSide.equals ("out"))
                            {
                                String pinName = edge.pinKeyFrom;
                                if (pinName.isEmpty ()) pinName = newKey;

                                MNode metadata = new MVolatile ();
                                setBindingOut (metadata, pinName, nodeTo.node.source.key (), newKey);
                                um.apply (new ChangeAnnotations (container.part, metadata));
                                handled = true;
                            }
                        }
                        else if (nodeTo == graphPanel.pinOut)
                        {
                            if (nodeFrom != graphPanel.pinIn  &&  edge.pinSideFrom.equals ("out"))
                            {
                                String pinName = newKey;
                                if (pinName.isEmpty ()) pinName = edge.pinKeyFrom;

                                MNode metadata = new MVolatile ();
                                setBindingOut (metadata, pinName, partFrom.source.key (), edge.pinKeyFrom);
                                um.apply (new ChangeAnnotations (container.part, metadata));

                                if (edge.nodeTo != null)
                                {
                                    if (! removeAuto (um, edge.nodeTo.node, edge.pinKeyTo))
                                    {
                                        MNode disconnect = new MVolatile ();
                                        clearBinding (disconnect, edge.pinKeyTo);
                                        um.apply (new ChangeAnnotations (edge.nodeTo.node, disconnect));
                                    }
                                }

                                handled = true;
                            }
                        }
                        else
                        {
                            if (nodeTo == graphPanel.pinIn  &&  newSide.isEmpty ()) newSide = "out";  // Landed on node rather than pin, so use pin name from other end of link.

                            if (! newSide.isEmpty ()  &&  ! newSide.equals (edge.pinSideFrom))  // Only connect to opposite type of pin.
                            {
                                GraphNode nodeAfter;  // Graph node that receives changes for new connection.
                                MNode connect = new MVolatile ();
                                if (newSide.equals ("in"))
                                {
                                    newKey = addAuto (um, nodeTo.node, newKey);
                                    nodeAfter = nodeTo;

                                    String partFromName;
                                    if (nodeFrom == graphPanel.pinIn) partFromName = "";
                                    else                              partFromName = partFrom.source.key ();
                                    setBinding (connect, newKey, partFromName, edge.pinKeyFrom);

                                    if (edge.nodeTo != null)  // Need to disconnect previous link. We already know that edge.nodeTo != nodeTo from higher-level test.
                                    {
                                        if (edge.nodeTo == graphPanel.pinOut)
                                        {
                                            clearBindingOut (um, edge.pinKeyTo);
                                        }
                                        else
                                        {
                                            if (! removeAuto (um, edge.nodeTo.node, edge.pinKeyTo))
                                            {
                                                MNode disconnect = new MVolatile ();
                                                clearBinding (disconnect, edge.pinKeyTo);
                                                um.apply (new ChangeAnnotations (edge.nodeTo.node, disconnect));
                                            }
                                        }
                                    }
                                }
                                else  // newSide is "out"
                                {
                                    nodeAfter = nodeFrom;

                                    String pinName = edge.pinKeyFrom;
                                    if (edge.nodeTo == null)  // new link, similar to "in" case above
                                    {
                                        pinName = addAuto (um, partFrom, pinName);
                                    }
                                    // else this simply changes which output pin is linked to the input pin

                                    if (newKey.isEmpty ()) newKey = pinName;

                                    String partFromName;
                                    if (nodeTo == graphPanel.pinIn) partFromName = "";
                                    else                            partFromName = nodeTo.node.source.key ();

                                    setBinding (connect, pinName, partFromName, newKey);
                                }

                                um.apply (new ChangeAnnotations (nodeAfter.node, connect));
                                handled = true;
                            }
                        }
                        if (! handled)  // because something about the connection was illegal
                        {
                            if (edge.pinSideTo == null)  // transient edge
                            {
                                graphPanel.edges.remove (edge);
                                graphPanel.repaint (edge.bounds);
                            }
                            else  // existing edge
                            {
                                edge.animate (null);
                            }
                        }
                    }
                    else  // from connection
                    {
                        // Release previous pin, if any.
                        if (edge.nodeTo != null)
                        {
                            DeleteAnnotation da = null;
                            if (edge.nodeTo == graphPanel.pinIn)
                            {
                                da = DeleteAnnotation.withName (partFrom, "gui", "pin");
                            }
                            else if (edge.nodeTo == graphPanel.pinOut)
                            {
                                da = DeleteAnnotation.withName (partFrom, "gui", "pin", "pass");
                            }
                            if (da != null) um.apply (da);
                        }

                        // Bind to new target.
                        if (nodeTo == graphPanel.pinIn  ||  nodeTo == graphPanel.pinOut)  // Tag connection as an input pin.
                        {
                            disconnect (um, variable);

                            String[] pieces = pinNew.split ("\\.", 2);
                            String pinName = "";
                            if (pieces.length > 1) pinName = pieces[1];

                            MNode metadata = new MVolatile ();
                            if (nodeTo == graphPanel.pinOut) metadata.set (pinName, "gui", "pin", "pass");
                            else                             metadata.set (pinName, "gui", "pin");
                            um.apply (new ChangeAnnotations (partFrom, metadata));
                        }
                        else  // Change endpoint
                        {
                            um.apply (new ChangeVariable (variable, edge.alias, nodeTo.node.source.key ()));
                        }
                    }
                }

                edge = null;
                um.endCompoundEdit ();
            }
            else if (selectStart != null)  // finish region select
            {
                Point p = me.getPoint ();
                Rectangle old = selectRegion;
                Rectangle r = new Rectangle (selectStart);
                r.add (p);
                selectStart = null;
                selectRegion = null;

                boolean toggle =  me.isShiftDown ()  ||  me.isControlDown ();
                if (! toggle) graphPanel.clearSelection ();

                List<GraphNode> selected = graphPanel.findNodesIn (r);
                for (int i = selected.size () - 1; i >= 0; i--)
                {
                    GraphNode g = selected.get (i);
                    if (toggle)
                    {
                        if (g.selected) selected.remove (i);
                        g.setSelected (! g.selected);
                    }
                    else
                    {
                        g.setSelected (true);
                    }
                }

                boolean focusNearest = true;
                if (toggle)
                {
                    // Move focus to graph if it is not already here. Prefer node associated with current part shown in property panel.
                    focusNearest = false;
                    GraphNode g = PanelModel.getGraphNode (KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ());
                    if (g == null)  // Current focus is not on a graph node, so pull it here.
                    {
                        focusNearest = true;  // Fallback
                        if (container.view != PanelEquations.NODE)
                        {
                            g = container.panelEquationTree.root.graph;
                            if (g != null)
                            {
                                g.takeFocusOnTitle ();
                                focusNearest = false;
                            }
                        }
                    }
                }
                if (focusNearest)  // Move focus to nearest graph node.
                {
                    GraphNode c;
                    if (selected.isEmpty ()) c = graphPanel.findNodeClosest (p);
                    else                     c = graphPanel.findNodeClosest (p, selected);
                    if (c != null) c.takeFocusOnTitle ();
                }

                graphPanel.repaint (old.union (r));
            }
            else if (orderPin != null)
            {
                String[] pieces = orderPin.split ("\\.");
                String pinSide = pieces[0];
                String pinKey  = pieces[1];
                orderPin = null;

                MNode       pinData;
                List<MNode> pinOrder;
                Rectangle   bounds;
                if (pinSide.equals ("in"))
                {
                    pinData  = container.part.pinIn;
                    pinOrder = container.part.pinInOrder;
                    bounds   = graphPanel.pinIn.pinOutBounds;
                }
                else  // pinSide is "out"
                {
                    pinData  = container.part.pinOut;
                    pinOrder = container.part.pinOutOrder;
                    bounds   = graphPanel.pinOut.pinInBounds;
                }

                int count = pinData.size ();
                int lineHeight = bounds.height / count;
                int y = me.getY () - bounds.y;
                int newIndex = y / lineHeight;
                newIndex = Math.min (count - 1, newIndex);
                newIndex = Math.max (0,         newIndex);
                MNode pin = pinData.child (pinKey);
                int oldIndex = pinOrder.indexOf (pin);

                if (newIndex != oldIndex)
                {
                    pinOrder.remove (oldIndex);
                    pinOrder.add (newIndex, pin);

                    MNode metadata = new MVolatile ();
                    for (int i = 0; i < count; i++) metadata.set (i, "gui", "pin", pinSide, pinOrder.get (i).key (), "order");
                    MainFrame.instance.undoManager.apply (new ChangeAnnotations (container.part, metadata));
                }
            }
        }

        public void clearBinding (MNode metadata, String pinName)
        {
            // These nodes won't actually be deleted, simply rendered inert.
            metadata.set ("", "gui", "pin", "in", pinName, "bind");
            metadata.set ("", "gui", "pin", "in", pinName, "bind", "pin");
        }

        /**
            Releases the reference to an exported output pin.
            Tries to determine if the pin information in the container's metadata is
            manually created or merely exists due to the export. In the latter case,
            the pin is completely removed. Otherwise, the reference is cleared but
            the other pin information is left to avoid destroying the user's work.
        **/
        public void clearBindingOut (UndoManager um, String pinName)
        {
            DeleteAnnotation da = null;
            if (hasAttributes ("out", pinName))
            {
                da = DeleteAnnotation.withName (container.part, "gui", "pin", "out", pinName, "bind");
            }
            else
            {
                da = DeleteAnnotation.withName (container.part, "gui", "pin", "out", pinName);
            }
            if (da != null) um.apply (da);
        }

        /**
            Checks if the given pin has any manually-created information.
            This indicates that it should not be deleted simply because a binding is removed.
        **/
        public boolean hasAttributes (String pinSide, String pinName)
        {
            MNode pin = container.part.source.child ("$metadata", "gui", "pin", pinSide, pinName);
            if (pin == null) return false;
            if (pin.child ("color") != null) return true;
            if (pin.child ("notes") != null) return true;
            if (pin.child ("order") != null) return true;
            return false;
        }

        public void setBinding (MNode metadata, String pinName, String fromNode, String fromPin)
        {
            metadata.set (fromNode, "gui", "pin", "in", pinName, "bind");
            metadata.set (fromPin,  "gui", "pin", "in", pinName, "bind", "pin");
        }

        public void setBindingOut (MNode metadata, String pinName, String fromNode, String fromPin)
        {
            metadata.set (fromNode, "gui", "pin", "out", pinName, "bind");
            metadata.set (fromPin,  "gui", "pin", "out", pinName, "bind", "pin");
        }

        /**
            Releases a connection binding.
        **/
        public void disconnect (UndoManager um, NodeVariable variable)
        {
            String value = "connect()";
            String original = variable.source.getOriginal ().get ();
            if (Operator.containsConnect (original)) value = original;
            if (! variable.source.get ().equals (value))  // Only create an edit action if the binding actually needs to be cleared.
            {
                um.apply (new ChangeVariable (variable, variable.source.key (), value));
            }
        }

        /**
            Subroutine of mouseReleased() that creates an auto-pin instance if needed.
            @return The name of pin that should ultimately receive the new binding.
            If this is an auto-pin, then it will be the newly-created copy.
            If this is a regular pin, then it will simply be the original name.
        **/
        public String addAuto (UndoManager um, NodePart node, String pinName)
        {
            if (! pinName.endsWith ("#")) return pinName;
            String pinBase = pinName.substring (0, pinName.length () - 1);

            // Scan for highest-index of existing copies.
            int index = 1;
            while (node.pinIn.child (pinBase + index) != null) index++;
            String pinBaseI = pinBase + index;

            // Create associated part(s).
            MNode parts = node.pinIn.child (pinName, "part");  // The subscribers to the auto pin.
            for (MNode p : parts)
            {
                String baseName = p.key ();
                String partName = baseName + index;
                NodePart template = (NodePart) node.child (baseName);
                MNode data = new MVolatile (null, partName);
                data.merge (template.source);

                int x = template.source.getInt ("$metadata", "gui", "bounds", "x");
                int y = template.source.getInt ("$metadata", "gui", "bounds", "y");
                data.set (x + 20 * index, "$metadata", "gui", "bounds", "x");
                data.set (y + 20 * index, "$metadata", "gui", "bounds", "y");

                data.set (index, "autoIndex");
                data.set (index, "autoCount");  // This will get replaced by better information below, if it is available.
                MNode pin = template.source.child ("$metadata", "gui", "pin");
                if (template.connectionBindings == null)
                {
                    // Need to update "autoCount" in other copied pins, since they don't have a shared part to reference.
                    for (int i = 1; i < index; i++)
                    {
                        NodePart sibling = (NodePart) node.child (baseName + i);
                        if (sibling != null) changeOrCreate (um, sibling, "autoCount", String.valueOf (index), false);
                    }
                }
                else
                {
                    MNode append = pin.child ("append");
                    String autoBase = pin.getOrDefault ("0", "append");

                    // Determine bindings
                    // First unbound endpoint should be IO block.
                    // First bound endpoint should be the destination part.
                    String   Aname = null;
                    String   Bname = null;
                    NodePart Bpart = null;
                    for (Entry<String,NodePart> e : template.connectionBindings.entrySet ())
                    {
                        NodePart endpoint = e.getValue ();
                        if (endpoint == null)
                        {
                            if (Aname == null) Aname = e.getKey ();
                        }
                        else
                        {
                            if (Bname == null)
                            {
                                Bname = e.getKey ();
                                Bpart = endpoint;
                            }
                        }
                    }

                    if (Bpart == null)
                    {
                        if (append != null) data.set (autoBase, "autoBase");
                    }
                    else
                    {
                        data.set (":" + Bname + ".autoCount", "autoCount");
                        if (append != null)
                        {
                            data.set (":" + Bname + ".autoBase" + index, "autoBase");
                            data.set (Aname + ".$n",                     Bname + ".autoN" + index);
                        }

                        // Set variables in B part
                        changeOrCreate (um, Bpart, "autoCount", String.valueOf (index), false);
                        if (append != null)
                        {
                            int index1 = index - 1;
                            if (index > 1) changeOrCreate (um, Bpart, "autoBase" + index, "autoBase" + index1 + "+autoN" + index1, true);
                            else           changeOrCreate (um, Bpart, "autoBase1", autoBase, true);
                            String n = Bpart.source.get ("$n");
                            if (n.isEmpty ()  ||  n.startsWith ("autoBase")) changeOrCreate (um, Bpart, "$n", "autoBase" + index + "+autoN" + index, false);
                        }
                    }
                }

                // Update input bindings
                //   for connections
                if (pin.get ().equals (pinName))  // must match auto pin name
                {
                    data.set (pinBaseI, "$metadata", "gui", "pin");
                }
                //   for parts that forward inputs
                for (MNode b : pin.childOrEmpty ("in"))
                {
                    if (b.get ("bind").isEmpty ()  &&  b.get ("bind", "pin").equals (pinName))  // must bind to IO block and match the auto pin name
                    {
                        data.set (pinBaseI, "$metadata", "gui", "pin", "in", b.key (), "bind", "pin");
                    }
                }

                um.apply (new AddPart (node, data));
            }

            // Set metadata on outer part, but only if needed.
            MNode data = new MVolatile ();
            // "notes" and "order" should not be copied. At present, no other keys need to be copied.
            MNode color = node.pinIn.child (pinName, "color");
            if (color != null)
            {
                data.set (color.get (), "gui", "pin", "in", pinBaseI, "color");
            }
            else if (parts.size () == 0)  // pin is not backed by inner parts
            {
                data.set ("", "gui", "pin", "in", pinBaseI);  // force pin to exist anyway
            }
            if (data.size () > 0) um.apply (new ChangeAnnotations (node, data));

            return pinBaseI;
        }

        public void changeOrCreate (UndoManager um, NodePart node, String name, String value, boolean createOnly)
        {
            NodeBase nb = node.child (name);
            if (nb == null)
            {
                MNode data = new MVolatile (value, name);
                um.apply (new AddVariable (node, node.getChildCount (), data));
            }
            else if (nb instanceof NodeVariable  &&  ! createOnly)
            {
                NodeVariable v = (NodeVariable) nb;
                um.apply (new ChangeVariable (v, name, value));
            }
        }

        /**
            Subroutine of mouseReleased() that frees an auto-pin instance.
        **/
        public boolean removeAuto (UndoManager um, NodePart node, String pinName)
        {
            // Parse pinName into base and index.
            int last = pinName.length () - 1;
            int i = last;
            while (i >= 0  &&  Character.isDigit (pinName.charAt (i))) i--;
            if (i < 0  ||  i == last) return false;  // illegal identifier or no suffix
            String baseName = pinName.substring (0, i + 1);
            int index = Integer.valueOf (pinName.substring (i + 1));

            if (node.pinIn.child (baseName + "#"        ) == null) return false;  // Not an auto-pin
            if (node.pinIn.child (baseName + (index + 1)) != null) return false;  // There is another generated pin above us, so don't delete.

            // Delete all contiguous unbound pins, in reverse order.
            NodeBase metadata = (NodeBase) node.child ("$metadata");
            MNode parts = node.pinIn.child (baseName + "#", "part");  // The subscribers to the auto pin.
            for (i = index; i >= 1; i--)
            {
                // Is the current pin deletable?
                String baseNameI = baseName + i;
                MNode pin = node.pinIn.child (baseNameI);
                if (pin == null) break;
                if (i < index  &&  pin.getFlag ("bind", "pin")) break;  // Stop at first bound pin, but ignore binding on the pin we were asked to delete in the function call.

                // Delete associated part(s).
                for (MNode p : parts)
                {
                    String partBaseName = p.key ();
                    String partName = partBaseName + i;
                    NodePart np = (NodePart) node.child (partName);
                    if (np == null) continue;

                    // Clean up connection target
                    if (np.connectionBindings != null)
                    {
                        // Determine "B" side
                        // It's redundant to look this up for every index to be deleted, however this keeps the code simple.
                        for (Entry<String,NodePart> e : np.connectionBindings.entrySet ())
                        {
                            NodePart Bpart = e.getValue ();
                            if (Bpart == null) continue;

                            MNode append = node.source.child (partBaseName, "$metadata", "gui", "pin", "append");
                            if (append != null)
                            {
                                NodeBase nb = Bpart.child ("autoBase" + i);
                                if (nb instanceof NodeVariable) um.apply (new DeleteVariable ((NodeVariable) nb, false));
                            }
                            break;
                        }
                    }

                    um.apply (new DeletePart (np, false));
                }

                // Delete pin metadata on node itself.
                NodeBase nb = AddAnnotation.findExact (metadata, true, "gui", "pin", "in", baseNameI);
                if (nb != null) um.apply (new DeleteAnnotation ((NodeAnnotation) nb, false));
            }
            // i is now the index of the highest remaining copy. It could be zero.

            // Update "autoCount" and $n
            // These is only possible after we know the final number of remaining copies.
            for (MNode p : parts)
            {
                String partBaseName = p.key ();
                NodePart template = (NodePart) node.child (partBaseName);
                if (template == null) continue;
                if (template.connectionBindings == null)
                {
                    for (int j = 1; j <= i; j++)
                    {
                        String partName = partBaseName + j;
                        NodePart np = (NodePart) node.child (partName);
                        if (np != null) changeOrCreate (um, np, "autoCount", String.valueOf (i), false);
                    }
                }
                else
                {
                    for (Entry<String,NodePart> e : template.connectionBindings.entrySet ())
                    {
                        NodePart Bpart = e.getValue ();
                        if (Bpart == null) continue;

                        NodeVariable n = (NodeVariable) Bpart.child ("$n");
                        if (i < 1)  // Since the last instance is going away, "autoCount" is no longer needed.
                        {
                            NodeBase nb = Bpart.child ("autoCount");
                            if (nb instanceof NodeVariable) um.apply (new DeleteVariable ((NodeVariable) nb, false));

                            if (n != null  &&  n.source.get ().startsWith ("autoBase")) um.apply (new DeleteVariable (n, false));
                        }
                        else  // "autoCount" is still needed, so update the value
                        {
                            changeOrCreate (um, Bpart, "autoCount", String.valueOf (i), false);

                            if (n == null  ||  n.source.get ().startsWith ("autoBase"))
                            {
                                changeOrCreate (um, Bpart, "$n", "autoBase" + i + "+autoN" + i, false);
                            }
                        }

                        break;
                    }
                }
            }

            return true;
        }

        public void actionPerformed (ActionEvent e)
        {
            mouseDragged (lastEvent);
        }
    }
}
