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
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.ViewportLayout;
import javax.swing.border.LineBorder;
import javax.swing.event.MouseInputAdapter;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;
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

        public GraphPanel ()
        {
            super (new GraphLayout ());
            layout = (GraphLayout) getLayout ();

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
                GraphNode gn = (GraphNode) c;
                if (gn.panelEquationTree != null) gn.panelEquationTree.clear ();
                gn.node.graph = null;
                gn.node.fakeRoot (false);
            }

            // Flush all data
            removeAll ();
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
                    GraphNode gn = new GraphNode (this, (NodePart) c);
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
                GraphNode gn = (GraphNode) c;

                // Build connection edges
                if (gn.node.connectionBindings != null)
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

                // Build pin edges
                if (gn.node.pinIn == null) continue;
                NodePart parent = (NodePart) gn.node.getTrueParent ();  // never null for a graph node
                for (MNode pin : gn.node.pinIn)
                {
                    // Find peer part
                    String bind = pin.get ("bind");
                    if (bind.isEmpty ()) continue;
                    NodeBase nb = parent.child (bind);
                    if (! (nb instanceof NodePart)) continue;
                    GraphNode peer = ((NodePart) nb).graph;

                    // Validate pin metadata
                    String bindPin = pin.get ("bind", "pin");
                    if (bindPin.isEmpty ()) continue;
                    if (peer.node.pinOut == null  ||  peer.node.pinOut.child (bindPin) == null) continue;

                    // Create edge
                    GraphEdge ge = new GraphEdge (peer, gn, "in", pin.key ());
                    gn.edgesIn.add (ge);
                    peer.edgesIn.add (ge);  // Treat as an incoming edge on both sides of the link.
                    edges.add (ge);
                    ge.updateShape (false);
                }
            }
            revalidate ();
        }

        public void rebuildEdges ()
        {
            for (Component c : getComponents ())
            {
                GraphNode gn = (GraphNode) c;
                gn.edgesIn.clear ();
                gn.edgesOut.clear ();
            }
            edges.clear ();
            buildEdges ();
        }

        /**
            Add a node to an existing graph.
            Must always be followed by a call to rebuildEdges() to update connections.
            These functions are separated to simplify code in undo objects.
        **/
        public void addPart (NodePart node)
        {
            GraphNode gn = new GraphNode (this, node);
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
                ((GraphNode) c).panelEquationTree.updateLock ();
            }
        }

        public void updateFilterLevel ()
        {
            for (Component c : getComponents ())
            {
                ((GraphNode) c).panelEquationTree.updateFilterLevel ();
            }
        }

        public void clearSelection ()
        {
            for (Component c : getComponents ())
            {
                ((GraphNode) c).setSelected (false);
            }
        }

        public List<GraphNode> getSelection ()
        {
            List<GraphNode> result = new ArrayList<GraphNode> ();
            for (Component c : getComponents ())
            {
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
                if (r.intersects (c.getBounds ())) result.add ((GraphNode) c);
            }
            return result;
        }

        public GraphNode findNodeClosest (Point p)
        {
            List<GraphNode> nodes = new ArrayList<GraphNode> ();
            for (Component c : getComponents ()) nodes.add ((GraphNode) c);
            return findNodeClosest (p, nodes);
        }

        public GraphNode findNodeClosest (Point p, List<GraphNode> nodes)
        {
            GraphNode result = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (GraphNode g : nodes)
            {
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
                GraphNode gn = (GraphNode) c;
                if (gn.node.source.key ().equals (name)) return gn;
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
                ((GraphNode) c).paintPins (g2, clip);  // Test clip bounds against pins. Paint pin if any overlap.
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
    }

    public class GraphLayout implements LayoutManager2
    {
        public Rectangle bounds = new Rectangle ();
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
            if (SwingUtilities.isRightMouseButton (me)  ||  me.isControlDown ())
            {
                // Context menus
                if (container.locked) return;
                Point p = me.getPoint ();
                GraphEdge e = graphPanel.findTipAt (p);
                if (e != null  &&  e.pinKeyFrom != null) e = null;  // Don't show arrow menu for pin-to-pin links.
                if (e != null)
                {
                    graphPanel.arrowEdge = e;
                    graphPanel.arrowMenu.show (graphPanel, p.x, p.y);
                }
                else
                {
                    container.getTitleFocus ().requestFocusInWindow ();
                    graphPanel.popupLocation = new Point ();
                    graphPanel.popupLocation.x = p.x - graphPanel.offset.x;
                    graphPanel.popupLocation.y = p.y - graphPanel.offset.y;
                    container.menuPopup.show (graphPanel, p.x, p.y);
                }
            }
            else if (SwingUtilities.isLeftMouseButton (me))
            {
                if (container.locked) return;
                Point p = me.getPoint ();
                edge = graphPanel.findTipAt (p);
                if (edge == null)
                {
                    // Check if a pin region is under the click.
                    GraphNode g = graphPanel.findNodeAt (p, true);  // Only a click in the pin zone will return non-null here. If it were in the graph node proper, the click would have been routed there instead.
                    if (g == null)  // bare background
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
                            edge = new GraphEdge (g, null, "in", pin.key ());
                        }
                        else if (g.pinOutBounds != null  &&  g.pinOutBounds.contains (p))
                        {
                            int lineHeight = g.pinOutBounds.height / g.node.pinOut.size ();
                            int y = p.y - g.pinOutBounds.y;
                            MNode pin = g.node.pinOutOrder.get (y / lineHeight);
                            edge = new GraphEdge (g, null, "out", pin.key ());
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
                if (edge != null)
                {
                    setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    edge.animate (p);  // Activates tipDrag
                }
            }
            else if (SwingUtilities.isMiddleMouseButton (me))
            {
                startPan = me.getPoint ();
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
                            MNode data = new MVolatile ();
                            data.set ("", "gui", "pin", "in", pin, "bind");  // This node won't actually be deleted, simply rendered inert.
                            data.set ("", "gui", "pin", "in", pin, "bind", "pin");
                            um.apply (new ChangeAnnotations (part, data));
                        }
                    }
                    else  // regular connector, possibly bound to pin
                    {
                        // Change to disconnected state

                        String value = "connect()";
                        String original = variable.source.getOriginal ().get ();
                        if (Operator.containsConnect (original)) value = original;

                        NodeBase pin = AddAnnotation.resolve (variable, "gui.pin");
                        if (pin == variable)
                        {
                            pin = null;
                        }
                        else  // found something
                        {
                            MNode m = ((NodeAnnotation) pin).folded;
                            if (! m.key ().equals ("pin")  ||  ! m.parent ().key ().equals ("gui")) pin = null;  // Verify that pin is actually "gui.pin"
                        }

                        if (pin != null)
                        {
                            um.addEdit (new CompoundEdit ());
                            um.apply (new DeleteAnnotation ((NodeAnnotation) pin, false));
                        }
                        um.apply (new ChangeVariable (variable, edge.alias, value));
                        um.endCompoundEdit ();
                    }
                }
                else if (nodeTo == edge.nodeTo)  // No change in target node
                {
                    // Usually, there is nothing to do but end the drag.
                    // However, if the target is specifically a pin, then need to update metadata.
                    boolean handled = false;
                    String pinNew = nodeTo.findPinAt (p);
                    if (variable == null)  // from pin
                    {
                        // In general, if a gesture is forbidden, then leave the pin unchanged (as opposed to deleting it).
                        // There are several ways for that to happen in this case.
                        if (! pinNew.isEmpty ())
                        {
                            String[] pieces = pinNew.split ("\\.", 2);
                            String newSide = pieces[0];
                            String newKey  = pieces[1];
                            if (! newKey.equals (edge.pinKeyTo)  &&  newSide.equals (edge.pinSideTo))  // Only do something if pin changed, but don't change sides.
                            {
                                // Move to new input pin.
                                // Must clear the old binding and set the new binding.
                                MNode data = new MVolatile ();
                                NodePart partTo = nodeTo.node;
                                if (edge.pinSideTo.equals ("in"))  // selected a different input
                                {
                                    String nameFrom = partFrom.source.key ();
                                    data.set ("",              "gui", "pin", "in", edge.pinKeyTo, "bind");
                                    data.set ("",              "gui", "pin", "in", edge.pinKeyTo, "bind", "pin");
                                    data.set (nameFrom,        "gui", "pin", "in", newKey,        "bind");
                                    data.set (edge.pinKeyFrom, "gui", "pin", "in", newKey,        "bind", "pin");
                                    um.apply (new ChangeAnnotations (partTo, data));
                                }
                                else  // edge.pinSideTo is "out" --> Selected a different output to draw from, while input pin remains the same.
                                {
                                    String nameTo = partTo.source.key ();
                                    data.set (nameTo, "gui", "pin", "in", edge.pinKeyFrom, "bind");
                                    data.set (newKey, "gui", "pin", "in", edge.pinKeyFrom, "bind", "pin");
                                    // The edge would need to be reversed, except that we regenerate all edges in ChangeAnnotations. 
                                    um.apply (new ChangeAnnotations (partFrom, data));
                                }
                                handled = true;
                            }
                            else if (edge.pinSideTo.equals ("out"))  // Edge goes back to original pin. May need to un-reverse the edge.
                            {
                                edge.reversePins ();
                            }
                        }
                    }
                    else  // from connection
                    {
                        String pinOld = variable.source.get ("$metadata", "gui", "pin");
                        if (! pinNew.equals (pinOld))  // target pin has changed
                        {
                            if (pinNew.isEmpty ())  // Possibly switched from pin to part
                            {
                                // Find pin metadata
                                NodeBase pin = AddAnnotation.resolve (variable, "gui.pin");
                                if (pin != variable)
                                {
                                    MNode m = ((NodeAnnotation) pin).folded;
                                    if (m.key ().equals ("pin")  &&  m.parent ().key ().equals ("gui"))
                                    {
                                        um.apply (new DeleteAnnotation ((NodeAnnotation) pin, false));
                                        handled = true;
                                    }
                                }
                            }
                            else
                            {
                                MNode data = new MVolatile ();
                                data.set (pinNew, "gui", "pin");
                                um.apply (new ChangeAnnotations (variable, data));
                                handled = true;
                            }
                        }
                    }

                    if (! handled) edge.animate (null);  // Stop drag mode and restore connection to normal.
                }
                else  // Connect to new endpoint
                {
                    String pinNew = nodeTo.findPinAt (p);
                    if (variable == null)  // from pin
                    {
                        boolean handled = false;
                        if (! pinNew.isEmpty ())
                        {
                            String[] pieces = pinNew.split ("\\.", 2);
                            String newSide = pieces[0];
                            String newKey  = pieces[1];
                            if (! newSide.equals (edge.pinSideFrom))  // Only connect to opposite type of pin.
                            {
                                GraphNode nodeAfter;  // Graph node that receives changes for new connection.
                                MNode connect = new MVolatile ();
                                if (newSide.equals ("in"))
                                {
                                    nodeAfter = nodeTo;
                                    String nameFrom = partFrom.source.key ();
                                    connect.set (nameFrom,        "gui", "pin", "in", newKey, "bind");
                                    connect.set (edge.pinKeyFrom, "gui", "pin", "in", newKey, "bind", "pin");

                                    if (edge.nodeTo != null)  // Need to disconnect previous link. We already know that edge.nodeTo != nodeTo from higher-level test.
                                    {
                                        MNode disconnect = new MVolatile ();
                                        disconnect.set ("", "gui", "pin", "in", edge.pinKeyTo, "bind");
                                        disconnect.set ("", "gui", "pin", "in", edge.pinKeyTo, "bind", "pin");
                                        um.addEdit (new CompoundEdit ());
                                        um.apply (new ChangeAnnotations (edge.nodeTo.node, disconnect));
                                    }
                                }
                                else  // newSide is "out", which means input pin stays the same, and it is linked to a diferent output pin.
                                {
                                    nodeAfter = nodeFrom;
                                    String nameTo = nodeTo.node.source.key ();
                                    connect.set (nameTo, "gui", "pin", "in", edge.pinKeyFrom, "bind");
                                    connect.set (newKey, "gui", "pin", "in", edge.pinKeyFrom, "bind", "pin");
                                }

                                um.apply (new ChangeAnnotations (nodeAfter.node, connect));
                                um.endCompoundEdit ();
                                handled = true;
                            }
                        }
                        if (! handled)
                        {
                            if (edge.pinSideTo == null)  // transient edge that did not get completed
                            {
                                graphPanel.edges.remove (edge);
                                graphPanel.repaint (edge.bounds);
                            }
                            else
                            {
                                edge.animate (null);
                            }
                        }
                    }
                    else  // from connection
                    {
                        if (pinNew != null)
                        {
                            um.addEdit (new CompoundEdit ());
                            MNode data = new MVolatile ();
                            data.set (pinNew, "gui", "pin");
                            um.apply (new ChangeAnnotations (variable, data));
                        }
                        um.apply (new ChangeVariable (variable, edge.alias, nodeTo.node.source.key ()));
                        um.endCompoundEdit ();
                    }
                }
                edge = null;
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
        }

        public void actionPerformed (ActionEvent e)
        {
            mouseDragged (lastEvent);
        }
    }
}
