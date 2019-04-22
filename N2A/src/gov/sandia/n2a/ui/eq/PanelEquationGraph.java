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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Enumeration;
import java.util.Map.Entry;

import javax.swing.JPanel;
import javax.swing.UIManager;

import gov.sandia.n2a.ui.eq.tree.NodePart;

@SuppressWarnings("serial")
public class PanelEquationGraph extends JPanel
{
    protected PanelEquations container;
    protected GraphLayout    layout;

    protected static Color background = new Color (0xF0F0F0);  // light gray

    public PanelEquationGraph (PanelEquations container)
    {
        super (new GraphLayout ());
        this.container = container;
        layout = (GraphLayout) getLayout ();
        setBackground (Color.white);
    }

    public void load ()
    {
        removeAll ();
        Enumeration<?> children = container.root.children ();
        while (children.hasMoreElements ())
        {
            Object c = children.nextElement ();
            if (c instanceof NodePart) add (new GraphNode ((NodePart) c));
        }

        // Scan children to set up connections
        for (Component c : getComponents ())
        {
            GraphNode gn = (GraphNode) c;
            if (gn.node.connectionBindings == null) continue;
            for (Entry<String,NodePart> e : gn.node.connectionBindings.entrySet ())
            {
                GraphNode endpoint = null;
                NodePart np = e.getValue ();
                if (np != null) endpoint = np.graph;
                gn.links.put (e.getKey (), endpoint);
                if (endpoint != null) endpoint.endpoints.add (gn);
            }
        }

        validate ();
        paintImmediately (getBounds ());
    }

    public boolean isOptimizedDrawingEnabled ()
    {
        // Because parts can overlap, we must return false.
        return false;
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
        g2.setColor (Color.black);
        g2.setStroke (new BasicStroke (3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Component c : getComponents ())
        {
            GraphNode gn = (GraphNode) c;
            Rectangle b0 = gn.getBounds ();
            Vector2 p0 = new Vector2 (b0.getCenterX (), b0.getCenterY ());
            int x0 = (int) p0.x;  // truncating double to int, so inaccurate, but we don't care
            int y0 = (int) p0.y;
            for (Entry<String,GraphNode> e : gn.links.entrySet ())
            {
                GraphNode endpoint = e.getValue ();
                if (endpoint == null)
                {
                    // TODO: Visualize unconnected endpoints
                }
                else
                {
                    Rectangle b1 = endpoint.getBounds ();
                    Vector2 p1 = new Vector2 (b1.getCenterX (), b1.getCenterY ());
                    Segment2 s = new Segment2 (p0, p1);
                    Vector2 o1 = intersection (s, b1);
                    if (o1 == null) continue;
                    int x1 = (int) o1.x;
                    int y1 = (int) o1.y;
                    g2.drawLine (x0, y0, x1, y1);

                    // Arrow head
                    double a = s.angle () - Math.PI;  // reverse direction
                    double da = Math.PI / 6;
                    Vector2 end = new Vector2 (o1, a + da, 10);
                    g2.drawLine ((int) end.x, (int) end.y, x1, y1);
                    end = new Vector2 (o1, a - da, 10);
                    g2.drawLine ((int) end.x, (int) end.y, x1, y1);
                }
            }
        }

        g2.dispose ();
    }

    /**
        Finds the point on the edge of the given rectangle nearest to the given point.
        Returns null if the given point is inside the rectangle.
    **/
    public static Vector2 intersection (Segment2 s0, Rectangle b)
    {
        double x0 = b.getMinX ();
        double y0 = b.getMinY ();
        double x1 = b.getMaxX ();
        double y1 = b.getMaxY ();

        // left edge
        Vector2 p0 = new Vector2 (x0, y0);
        Vector2 p1 = new Vector2 (x0, y1);
        double t = s0.intersection (new Segment2 (p0, p1));

        // bottom edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.x = x1;
        t = Math.min (t, s0.intersection (new Segment2 (p0, p1)));

        // right edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.y = y0;
        t = Math.min (t, s0.intersection (new Segment2 (p0, p1)));

        // top edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.x = x0;
        t = Math.min (t, s0.intersection (new Segment2 (p0, p1)));

        if (t > 1) return null;
        return s0.paramtetricPoint (t);
    }

    public void updateUI ()
    {
        GraphNode.RoundedBorder.updateUI ();
        background = UIManager.getColor ("SplitPane.background");
    }

    public static class Vector2
    {
        double x;
        double y;

        public Vector2 (double x, double y)
        {
            this.x = x;
            this.y = y;
        }

        public Vector2 (Vector2 origin, double angle, double length)
        {
            x = origin.x + Math.cos (angle) * length;
            y = origin.y + Math.sin (angle) * length;
        }

        public Vector2 add (Vector2 that)
        {
            return new Vector2 (x + that.x, y + that.y);
        }

        public Vector2 subtract (Vector2 that)
        {
            return new Vector2 (x - that.x, y - that.y);
        }

        public Vector2 multiply (double a)
        {
            return new Vector2 (x * a, y * a);
        }

        public double cross (Vector2 that)
        {
            return x * that.y - y * that.x;
        }

        public String toString ()
        {
            return "(" + x + "," + y + ")";
        }
    }

    public static class Segment2
    {
        Vector2 a;
        Vector2 b;

        public Segment2 (Vector2 a, Vector2 b)
        {
            this.a = a;
            this.b = b.subtract (a);
        }

        /**
            Finds the point where this segment and that segment intersect.
            Result is a number in [0,1].
            If the segments are parallel or do not intersect, then the result is infinity.

            From https://stackoverflow.com/questions/563198/how-do-you-detect-where-two-line-segments-intersect
            which is in turn based on "Intersection of two lines in three-space" by Ronald Goldman,
            published in Graphics Gems, page 304.

            This is equivalent to a linear solution which involves matrix inversion
            (such as https://blogs.sas.com/content/iml/2018/07/09/intersection-line-segments.html).
            However, this approach offers a clear sequence of early-outs that make it efficient.
        **/
        public double intersection (Segment2 that)
        {
            double rs = b.cross (that.b);
            if (rs == 0) return Double.POSITIVE_INFINITY;  // Singular, so no solution
            Vector2 qp = that.a.subtract (a);
            double t = qp.cross (that.b) / rs;  // Parameter for point on this segment
            if (t < 0  ||  t > 1) return Double.POSITIVE_INFINITY;  // Not between start and end points
            double u = qp.cross (b) / rs;  // Parameter for point on that segment
            if (u < 0  ||  u > 1) return Double.POSITIVE_INFINITY;
            return t;
        }

        public Vector2 paramtetricPoint (double t)
        {
            return a.add (b.multiply (t));
        }

        public double angle ()
        {
            return Math.atan2 (b.y, b.x);
        }

        public String toString ()
        {
            return a + "->" + a.add (b);
        }
    }

    public static class GraphLayout implements LayoutManager2
    {
        public Rectangle bounds = new Rectangle ();
        public boolean   needSize;

        public void addLayoutComponent (String name, Component comp)
        {
            addLayoutComponent (comp, name);
        }

        public void addLayoutComponent (Component comp, Object constraints)
        {
            Dimension d = comp.getPreferredSize ();
            comp.setSize (d);
            Point p = comp.getLocation ();
            bounds.union (new Rectangle (p, d));
        }

        public void removeLayoutComponent (Component comp)
        {
            // If we remove a component that was stretching the bounds, then need to recalculate.
            Dimension d = comp.getSize ();
            Point     p = comp.getLocation ();
            Rectangle r = new Rectangle (p, d);
            if (r.getMinX () == bounds.getMinX ()  ||  r.getMinY () == bounds.getMinY ()  ||  r.getMaxX () == bounds.getMaxX ()  ||  r.getMaxY () == bounds.getMaxY ()) needSize = true;
        }

        public Dimension preferredLayoutSize (Container target)
        {
            if (! needSize) return bounds.getSize ();
            bounds = new Rectangle ();
            for (Component c : target.getComponents ())
            {
                Dimension d = c.getSize ();
                Point     p = c.getLocation ();
                bounds.union (new Rectangle (p, d));
            }
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
        }

        public void componentMoved (Component comp, Rectangle oldBounds)
        {
            if (oldBounds.getMinX () == bounds.getMinX ()  ||  oldBounds.getMinY () == bounds.getMinY ()  ||  oldBounds.getMaxX () == bounds.getMaxX ()  ||  oldBounds.getMaxY () == bounds.getMaxY ())
            {
                needSize = true;
            }
            else
            {
                Dimension d = comp.getSize ();
                Point     p = comp.getLocation ();
                bounds.union (new Rectangle (p, d));
            }
        }
    }
}
