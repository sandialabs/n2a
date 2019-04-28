/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.ViewportLayout;
import javax.swing.event.MouseInputAdapter;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.eq.GraphEdge.Vector2;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;

@SuppressWarnings("serial")
public class PanelEquationGraph extends JScrollPane
{
    protected PanelEquations   container;
    protected GraphPanel       graphPanel;
    protected Map<MNode,Point> focusCache = new HashMap<MNode,Point> ();

    // Convenience references
    protected JViewport  vp;
    protected JScrollBar hsb;
    protected JScrollBar vsb;

    protected static Color background = new Color (0xF0F0F0);  // light gray

    public PanelEquationGraph (PanelEquations container)
    {
        this.container = container;
        graphPanel = new GraphPanel ();
        setViewportView (graphPanel);
        setAutoscrolls (true);

        vp  = getViewport ();
        hsb = getHorizontalScrollBar ();
        vsb = getVerticalScrollBar ();

        hsb.addAdjustmentListener (new AdjustmentListener ()
        {
            public void adjustmentValueChanged (AdjustmentEvent e)
            {
                Point p = vp.getViewPosition ();
                int i = e.getValue ();
                if (i != p.x)
                {
                    p.x = i;
                    vp.setViewPosition (p);
                }
            }
        });

        vsb.addAdjustmentListener (new AdjustmentListener ()
        {
            public void adjustmentValueChanged (AdjustmentEvent e)
            {
                Point p = vp.getViewPosition ();
                int i = e.getValue ();
                if (i != p.y)
                {
                    p.y = i;
                    vp.setViewPosition (p);
                }
            }
        });

        addMouseWheelListener (new MouseWheelListener ()
        {
            public void mouseWheelMoved (MouseWheelEvent e)
            {
                if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
                {
                    // Should really get scaling from scrollbar, but since we configure it to do pixel increments,
                    // we can hard code the multiplier in terms of how many pixels the scroll wheel should move.
                    if (vsb.isVisible ()) vsb.setValue (vsb.getValue () + e.getUnitsToScroll () * 15);
                }
            }
        });

        MouseAdapter mouseListener = new GraphMouseListener ();
        addMouseListener (mouseListener);
        addMouseMotionListener (mouseListener);
    }

    public void saveFocus (MNode record)
    {
        Point focus = vp.getViewPosition ();
        focus.x -= graphPanel.offset.x;
        focus.y -= graphPanel.offset.y;
        focusCache.put (record, focus);
    }

    public void load ()
    {
        graphPanel.clear ();
        graphPanel.load ();  // also does revalidate()
        paintImmediately (getBounds ());
    }

    public void recordDeleted ()
    {
        graphPanel.clear ();
        graphPanel.revalidate ();
        paintImmediately (getBounds ());
    }

    public void updateUI ()
    {
        GraphNode.RoundedBorder.updateUI ();
        background = UIManager.getColor ("ScrollPane.background");
    }

    public void doLayout ()
    {
        super.doLayout ();

        // Update scroll bars
        Point p          = vp.getViewPosition ();
        Dimension size   = vp.getViewSize ();
        Dimension extent = vp.getExtentSize ();
        if (size.width > extent.width)
        {
            hsb.setValues (p.x, extent.width, 0, size.width);
        }
        if (size.height > extent.height)
        {
            vsb.setValues (p.y, extent.height, 0, size.height);
        }
    }

    public JViewport createViewport ()
    {
        return new JViewport ()
        {
            public LayoutManager createLayoutManager ()
            {
                return new ViewportLayout ()
                {
                    public void layoutContainer(Container parent)
                    {
                        // The original version of this code (in OpenJDK) justifies (moves) the view if it is smaller
                        // than the viewport extent. We don't want to move the viewport, so simply set its preferred size.
                        // The original code also expands the view to fill the viewport. Presumably this is to
                        // ensure everything gets painted. However, the display seems to work fine without doing that,
                        // and the simpler approach seems to produce more reliable behavior.
                        vp.setViewSize (graphPanel.getPreferredSize ());
                    }
                };
            }
        };
    }

    public class GraphMouseListener extends MouseInputAdapter
    {
        Point     startPan = null;
        GraphEdge edge     = null;

        public void mouseMoved (MouseEvent me)
        {
            GraphEdge e = graphPanel.findTipAt (me.getPoint ());
            if (e == null) setCursor (Cursor.getDefaultCursor ());
            else           setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
        }

        public void mousePressed (MouseEvent me)
        {
            switch (me.getButton ())
            {
                case MouseEvent.BUTTON1:
                    Point p = me.getPoint ();
                    edge = graphPanel.findTipAt (p);
                    if (edge != null)
                    {
                        setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                        edge.animate (p);
                    }
                    break;
                case MouseEvent.BUTTON2:
                    startPan = me.getPoint ();
                    setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    break;
            }
        }

        public void mouseDragged (MouseEvent me)
        {
            if (edge != null)
            {
                edge.animate (me.getPoint ());
            }
            else if (startPan != null)
            {
                Point now = me.getPoint ();
                int dx = now.x - startPan.x;
                int dy = now.y - startPan.y;
                if (dx != 0  &&  hsb.isVisible ())
                {
                    int old = hsb.getValue ();
                    hsb.setValue (old - dx);
                    startPan.x += old - hsb.getValue ();
                }
                if (dy != 0  &&  vsb.isVisible ())
                {
                    int old = vsb.getValue ();
                    vsb.setValue (old - dy);
                    startPan.y += old - vsb.getValue ();
                }
            }
        }

        public void mouseReleased (MouseEvent me)
        {
            startPan = null;
            setCursor (Cursor.getPredefinedCursor (Cursor.DEFAULT_CURSOR));

            if (edge != null)  // Finish assigning endpoint
            {
                edge.tipDrag = false;

                PanelModel mep = PanelModel.instance;
                GraphNode nodeFrom = edge.nodeFrom;
                NodePart part = nodeFrom.node;
                NodeVariable variable = (NodeVariable) part.child (edge.alias);  // There should always be a variable with the alias as its name.

                GraphNode nodeTo = graphPanel.findNodeAt (me.getPoint ());
                if (nodeTo == null)  // Disconnect the edge
                {
                    String value = "connect()";
                    MPart mchild = variable.source;
                    if (mchild.isOverridden ())
                    {
                        String original = mchild.getOriginal ().get ();
                        if (Operator.containsConnect (original)) value = original;
                    }
                    mep.undoManager.add (new ChangeVariable (variable, edge.alias, value));
                }
                else if (nodeTo == edge.nodeTo)  // No change
                {
                    edge.animate (null);
                }
                else  // Connect to new endpoint
                {
                    mep.undoManager.add (new ChangeVariable (variable, edge.alias, nodeTo.node.source.key ()));
                }
                edge = null;
            }
        }
    }

    public class GraphPanel extends JPanel
    {
        protected GraphLayout     layout;  // For ease of access, to avoid calling getLayout() all the time.
        protected List<GraphEdge> edges  = new ArrayList<GraphEdge> (); // Note that GraphNodes are stored directly as Swing components.
        protected Point           offset = new Point ();  // Offset from persistent coordinates to pixels. Add this to a stored (x,y) value to get non-negative coordinates that can be painted.

        public GraphPanel ()
        {
            super (new GraphLayout ());
            layout = (GraphLayout) getLayout ();
        }

        public boolean isOptimizedDrawingEnabled ()
        {
            // Because parts can overlap, we must return false.
            return false;
        }

        public void clear ()
        {
            removeAll ();
            edges.clear ();
            layout.bounds = new Rectangle ();
            offset = new Point ();
            vp.setViewPosition (new Point ());
            hsb.setValue (0);
            vsb.setValue (0);
        }

        public void load ()
        {
            Enumeration<?> children = container.root.children ();
            boolean newLayout = children.hasMoreElements ();
            while (children.hasMoreElements ())
            {
                Object c = children.nextElement ();
                if (c instanceof NodePart)
                {
                    GraphNode gn = new GraphNode (this, (NodePart) c);
                    add (gn);
                    if (gn.getX () != 0  ||  gn.getY () != 0) newLayout = false;
                }
            }

            Component[] components = getComponents ();
            if (newLayout)
            {
                // TODO: use potential-field method, such as "Drawing Graphs Nicely Using Simulated Annealing" by Davidson & Harel (1996).

                // For now, a very simple layout. Arrange in a grid with some space between nodes.
                int columns = (int) Math.sqrt (components.length);  // Truncate, so more rows than columns.
                int gap = 100;
                int x = 0;
                int y = 0;
                for (int i = 0; i < components.length; i++)
                {
                    Component c = components[i];
                    if (i % columns == 0)
                    {
                        x = gap;
                        y += gap;
                    }
                    c.setLocation (x, y);
                    MNode bounds = ((GraphNode) c).node.source.childOrCreate ("$metadata", "bounds");
                    bounds.set (x, "x");
                    bounds.set (y, "y");
                    x += c.getWidth () + gap;
                }

                // TODO: the equation tree should be rebuilt, so that new metadata nodes become visible.
            }
            else
            {
                // Force everything to be in positive coordinates.
                doLayout ();
            }

            // Scan children to set up connections
            for (Component c : components)
            {
                GraphNode gn = (GraphNode) c;
                if (gn.node.connectionBindings == null) continue;

                GraphEdge A = null;
                GraphEdge B = null;
                for (Entry<String,NodePart> e : gn.node.connectionBindings.entrySet ())
                {
                    GraphNode endpoint = null;
                    NodePart np = e.getValue ();
                    if (np != null) endpoint = np.graph;

                    GraphEdge ge = new GraphEdge (gn, endpoint, e.getKey ());
                    edges.add (ge);
                    gn.edgesOut.add (ge);
                    if (endpoint != null) endpoint.edgesIn.add (ge);

                    if (A == null) A = ge;  // Not necessarily same as endpoint variable named "A" in part.
                    else           B = ge;
                }
                if (gn.edgesOut.size () == 2)
                {
                    A.edgeOther = B;
                    B.edgeOther = A;
                }

                for (GraphEdge ge : gn.edgesOut)
                {
                    ge.updateShape (false);
                    if (ge.bounds != null) layout.bounds = layout.bounds.union (ge.bounds);
                }
            }

            revalidate ();
            Point focus = focusCache.get (container.record);
            if (focus == null)
            {
                focus = new Point ();  // (0,0)
            }
            else
            {
                focus.x += graphPanel.offset.x;
                focus.y += graphPanel.offset.y;
                focus.x = Math.max (0, focus.x);
                focus.y = Math.max (0, focus.y);
                Dimension extent = vp.getExtentSize ();
                focus.x = Math.min (focus.x, Math.max (0, layout.bounds.width  - extent.width));
                focus.y = Math.min (focus.y, Math.max (0, layout.bounds.height - extent.height));
            }
            vp.setViewPosition (focus);
        }

        public GraphEdge findTipAt (Point p)
        {
            Vector2 p2 = new Vector2 (p.x, p.y);
            for (GraphEdge e : edges)
            {
                if (e.tip != null  &&  e.tip.distance (p2) < GraphEdge.arrowheadLength) return e;
            }
            return null;
        }

        public GraphNode findNodeAt (Point p)
        {
            for (Component c : getComponents ())
            {
                // p is relative to the container, whereas Component.contains() is relative to the component itself.
                if (c.contains (p.x - c.getX (), p.y - c.getY ())) return (GraphNode) c;
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

            // Draw connection edges
            g2.setStroke (new BasicStroke (GraphEdge.strokeThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (GraphEdge e : edges)
            {
                if (e.bounds.intersects (clip)) e.paintComponent (g2);
            }

            g2.dispose ();
        }
    }

    public class GraphLayout implements LayoutManager2
    {
        public Rectangle bounds = new Rectangle ();

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

        /**
            Estimates the exact size of the view panel, accounting for possible
            presence of scroll bars and possible shift to be applied by layoutContainer().
            This anticipates decisions make by ScrollPaneLayout, and takes the place of
            adjustments made by ViewportLayout.
        **/
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
            if (bounds.x >= 0  &&  bounds.y >= 0) return;

            GraphPanel gp = (GraphPanel) target;
            JViewport  vp = (JViewport) gp.getParent ();
            int dx = Math.max (-bounds.x, 0);
            int dy = Math.max (-bounds.y, 0);
            bounds.translate (dx, dy);
            gp.offset.translate (dx, dy);
            Point p = vp.getViewPosition ();
            p.translate (dx, dy);
            vp.setViewPosition (p);

            // None of the following code is allowed to call componentMoved().
            for (Component c : target.getComponents ())
            {
                p = c.getLocation ();
                p.translate (dx, dy);
                c.setLocation (p);
            }
            for (GraphEdge ge : gp.edges)
            {
                ge.updateShape (false);
            }
        }

        public boolean componentMoved (Rectangle newBounds, Rectangle oldBounds)
        {
            Rectangle myOldBounds = bounds;
            bounds = bounds.union (newBounds);
            return ! bounds.equals (myOldBounds);
        }
    }
}
