/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;

public class GraphEdge
{
    protected GraphNode nodeFrom;  // The connection
    protected GraphNode nodeTo;    // The endpoint that this edge goes to
    protected GraphEdge edgeOther; // For binary connections only, the edge to the other endpoint. Used to coordinate a smooth curve through the connection node.
    protected String    alias;     // Name of endpoint variable in connection part.

    protected Shape     shape;
    protected Vector2   label;
    protected Rectangle textBox;
    protected Rectangle bounds = new Rectangle (0, 0, -1, -1);  // empty. Allows call to animate() on brand-new edges that have not previously called updateShape().
    protected Vector2   tip;
    protected boolean   tipDrag;
    protected Vector2   anchor;  // When non-null, use this as start for tip drag.

    protected static double arrowheadAngle  = Math.PI / 5;
    protected static double arrowheadLength = 10;
    protected static float  strokeThickness = 3;
    protected static int    nameTopPad      = 1;
    protected static int    nameSidePad     = 2;

    public GraphEdge (GraphNode nodeFrom, GraphNode nodeTo, String alias)
    {
        this.nodeFrom = nodeFrom;
        this.nodeTo   = nodeTo;
        this.alias    = alias;
    }

    /**
        Updates our cached shape information based current state of graph nodes.
    **/
    public void updateShape (boolean updateOther)
    {
        Rectangle Cbounds = nodeFrom.getBounds ();
        Vector2 c = new Vector2 (Cbounds.getCenterX (), Cbounds.getCenterY ());

        Rectangle Abounds = null;
        Vector2   a       = null;
        if (tipDrag)
        {
            a = tip;
            if (anchor != null) c = anchor;
        }
        else if (nodeTo != null)
        {
            Abounds = nodeTo.getBounds ();
            a       = new Vector2 (Abounds.getCenterX (), Abounds.getCenterY ());
        }

        Vector2 ba = null;  // Non-null for binary connections that also need a curve rather than straight line.
        Vector2 c2c = null;
        if (edgeOther != null  &&  nodeTo != null  &&  edgeOther.nodeTo != null)
        {
            Vector2 b;
            if (edgeOther.tipDrag)
            {
                b = edgeOther.tip;
            }
            else
            {
                Rectangle Bbounds = edgeOther.nodeTo.getBounds ();
                b = new Vector2 (Bbounds.getCenterX (), Bbounds.getCenterY ());
            }

            ba = a.subtract (b);  // vector from b -> a
            Vector2 avg = a.add (b).multiply (0.5);  // average position
            c2c = c.subtract (avg);
            double c2cLength = c2c.length ();
            double baLength = ba.length ();
            if (c2cLength > baLength)  // Draw curved lines.
            {
                c2c = c2c.normalize ();
                if (baLength > 0)
                {
                    ba = ba.normalize ();
                }
                else  // Both A and B nodes are at exactly the same place. Create vector perpendicular to c2c.
                {
                    ba = new Vector2 (-c2c.y, c2c.x);
                    if (nodeFrom.edgesOut.get (0) == this) ba = ba.multiply (-1);
                }
            }
            else  // Draw straight lines.
            {
                ba = null;
            }

            // If needed, update shape parameters of the other edge.
            // The two possible sources of a call are nodeFrom and nodeTo.
            // If nodeTo, then we propagate the update to our peer edge.
            // However, if this is a self-connection, then we are our peer edge, so don't start an infinite loop.
            if (updateOther) edgeOther.updateShape (false);
        }

        Graphics g = nodeFrom.getGraphics ();
        FontMetrics fm = nodeFrom.getFontMetrics (nodeFrom.getFont ());
        Rectangle2D tb = fm.getStringBounds (alias, g);
        double tw = tb.getWidth ();
        double th = tb.getHeight ();

        double tipAngle = 0;
        Vector2 root = null;
        double nodeAngle = Math.atan ((double) Cbounds.height / Cbounds.width);

        if (nodeTo == null)  // Unconnected endpoint
        {
            if (! tipDrag)
            {
                // Distribute rays around node. This method is limited to 8 positions,
                // but no sane person would have more than an 8-way connection.
                int count = nodeFrom.edgesOut.size ();
                int index = nodeFrom.edgesOut.indexOf (this);
                double angle = index * Math.PI * 2 / count;
                tip = new Vector2 (angle);

                // Scale direction vector until it ends far enough from border
                // There are really only two cases: 1) coming out the left or right, 2) coming out the top or bottom
                // To decide, reduce angle to first quadrant and compare with node's own diagonal.
                double absAngle = tip.absAngle ();
                double length;
                if (absAngle > nodeAngle)  // top or bottom
                {
                    length = (Cbounds.height / 2 + th + arrowheadLength + strokeThickness) / Math.abs (tip.y);
                }
                else  // left or right
                {
                    length = (Cbounds.width / 2 + tw + arrowheadLength + strokeThickness) / Math.abs (tip.x);
                }
                tip = tip.multiply (length).add (c);
            }
            root = intersection (new Segment2 (c, tip), Cbounds);
            if (root == null)  // tip is inside Cbounds
            {
                shape  = null;
                label  = null;
                bounds = new Rectangle (0, 0, -1, -1);  // empty, so won't affect union(), and will return false from intersects()
                return;
            }
            shape = new Line2D.Double (c.x, c.y, tip.x, tip.y);
            tipAngle = new Segment2 (tip, c).angle ();
        }
        else if (ba == null)  // Draw straight line.
        {
            Segment2 s = new Segment2 (a, c);
            if (! tipDrag) tip = intersection (s, Abounds);  // tip can be null if c is inside Abounds
            root = intersection (s, Cbounds);  // root can be null if a is inside Cbounds
            if (tip == null  ||  root == null)
            {
                shape  = null;
                label  = null;
                bounds = new Rectangle (0, 0, -1, -1);
                return;
            }
            shape = new Line2D.Double (c.x, c.y, tip.x, tip.y);
            tipAngle = s.angle ();
        }
        else  // Draw curve.
        {
            if (tipDrag)
            {
                tipAngle = c2c.angle ();
            }
            else
            {
                double Alength = Abounds.getWidth () + Abounds.getHeight ();  // far enough to get from center of endpoint to outside of bounds
                Vector2 o = a.add (c2c.multiply (Alength));  // "outside" point in the direction of the connection
                Segment2 s = new Segment2 (a, o);  // segment from center of endpoint to outside point
                tip = intersection (s, Abounds);  // point on the periphery of the endpoint
                tipAngle = s.angle ();
            }

            double length = c.distance (tip) / 3;
            Vector2 w1 = tip.add (c2c.multiply (length));
            Vector2 w2 = c.add (ba.multiply (length));
            shape = new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, w2.x, w2.y, c.x, c.y);

            Spline spline = new Spline ((CubicCurve2D) shape);
            root = intersection (spline, Cbounds);  // on boundary of c
            if (root == null)
            {
                shape  = null;
                label  = null;
                bounds = new Rectangle (0, 0, -1, -1);
                return;
            }
        }

        // Arrow head
        Path2D path = new Path2D.Double (shape);
        Vector2 end = new Vector2 (tip, tipAngle + arrowheadAngle, arrowheadLength);
        path.append (new Line2D.Double (end.x, end.y, tip.x, tip.y), false);
        end = new Vector2 (tip, tipAngle - arrowheadAngle, arrowheadLength);
        path.append (new Line2D.Double (tip.x, tip.y, end.x, end.y), false);
        shape = path;

        bounds = shape.getBounds ();
        int t = (int) Math.ceil (strokeThickness / 2);
        bounds.grow (t, t);

        // Name
        label = new Vector2 (0, 0);
        double absAngle = root.subtract (c).absAngle ();
        if (absAngle > nodeAngle)  // top or bottom
        {
            label.x = root.x - tw / 2;
            if (root.y < c.y) label.y = root.y - th - nameTopPad;
            else              label.y = root.y      + nameTopPad;
        }
        else  // left or right
        {
            if (root.x < c.x) label.x = root.x - tw - nameSidePad;
            else              label.x = root.x      + nameSidePad;
            label.y = root.y - th / 2;
        }

        textBox = new Rectangle ();
        textBox.x      = (int) label.x - nameSidePad;
        textBox.y      = (int) label.y - nameTopPad;
        textBox.width  = (int) Math.ceil (tw) + 2 * nameSidePad;
        textBox.height = (int) Math.ceil (th) + 2 * nameTopPad;
        bounds = bounds.union (textBox);
        // tb gives position of top-left corner relative to baseline.
        // We want baseline relative to top-left, so subtract to reverse vector.
        label.x -= tb.getX ();
        label.y -= tb.getY ();
    }

    public void paintComponent (Graphics g)
    {
        if (shape == null) return;

        // If this class gets changed into a proper Swing component (such a JPanel),
        // then handle g in the appropriate manner. For now, we simply assume that
        // the caller is using us as subroutine, and that g is a fully configured Graphics2D,
        // including stroke, color and rendering hints.
        Graphics2D g2 = (Graphics2D) g;

        g2.setColor (Color.black);
        g2.draw (shape);

        if (alias.isEmpty ()) return;
        g2.setColor (new Color (0xD0FFFFFF, true));
        g2.fill (textBox);
        g2.setColor (Color.black);
        g2.drawString (alias, (float) label.x, (float) label.y);
    }

    public void animate (Point p)
    {
        if (p == null)
        {
            tipDrag = false;
        }
        else
        {
            tipDrag = true;
            tip.x = p.x;
            tip.y = p.y;
        }
        Rectangle paintRegion = bounds;
        if (edgeOther != null) paintRegion = paintRegion.union (edgeOther.bounds);
        updateShape (true);
        paintRegion = paintRegion.union (bounds);
        GraphPanel parent = nodeFrom.parent;
        parent.layout.componentMoved (bounds);
        if (edgeOther != null)
        {
            paintRegion = paintRegion.union (edgeOther.bounds);
            parent.layout.componentMoved (edgeOther.bounds);
        }
        parent.repaint (paintRegion);
    }

    /**
        Finds the point on the edge of the given rectangle that intersects with the given line segment.
        Returns null if the segment does not cross the perimeter of the rectangle.
    **/
    public static Vector2 intersection (Segment2 s, Rectangle b)
    {
        double x0 = b.getMinX ();
        double y0 = b.getMinY ();
        double x1 = b.getMaxX ();
        double y1 = b.getMaxY ();

        // left edge
        Vector2 p0 = new Vector2 (x0, y0);
        Vector2 p1 = new Vector2 (x0, y1);
        double t = s.intersection (new Segment2 (p0, p1));

        // bottom edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.x = x1;
        t = Math.min (t, s.intersection (new Segment2 (p0, p1)));

        // right edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.y = y0;
        t = Math.min (t, s.intersection (new Segment2 (p0, p1)));

        // top edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.x = x0;
        t = Math.min (t, s.intersection (new Segment2 (p0, p1)));

        if (t > 1) return null;
        return s.paramtetricPoint (t);
    }

    /**
        Finds the point on the edge of the given rectangle that intersects with the given cubic spline.
        Returns null if the spline does not cross the perimeter of the rectangle.
    **/
    public static Vector2 intersection (Spline s, Rectangle b)
    {
        double x0 = b.getMinX ();
        double y0 = b.getMinY ();
        double x1 = b.getMaxX ();
        double y1 = b.getMaxY ();

        // left edge
        Vector2 p0 = new Vector2 (x0, y0);
        Vector2 p1 = new Vector2 (x0, y1);
        double t = s.intersection (new Segment2 (p0, p1));

        // bottom edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.x = x1;
        t = Math.min (t, s.intersection (new Segment2 (p0, p1)));

        // right edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.y = y0;
        t = Math.min (t, s.intersection (new Segment2 (p0, p1)));

        // top edge
        p0.x = p1.x;
        p0.y = p1.y;
        p1.x = x0;
        t = Math.min (t, s.intersection (new Segment2 (p0, p1)));

        if (t > 1) return null;
        return s.paramtetricPoint (t);
    }

    public static class Vector2
    {
        double x;
        double y;

        public Vector2 (double angle)
        {
            x = Math.cos (angle);
            y = Math.sin (angle);
        }

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

        public Vector2 divide (double a)
        {
            return new Vector2 (x / a, y / a);
        }

        public double cross (Vector2 that)
        {
            return x * that.y - y * that.x;
        }

        public double dot (Vector2 that)
        {
            return x * that.x + y * that.y;
        }

        public double distance (Vector2 that)
        {
            double dx = x - that.x;
            double dy = y - that.y;
            return Math.sqrt (dx * dx + dy * dy);
        }

        public double length ()
        {
            return Math.sqrt (x * x + y * y);
        }

        public double angle ()
        {
            return Math.atan2 (y, x);
        }

        public double absAngle ()
        {
            if (x == 0)
            {
                if (y == 0) return 0;
                return Math.PI / 2;
            }
            return Math.atan (Math.abs (y) / Math.abs (x));
        }

        public Vector2 normalize ()
        {
            return divide (length ());  // could produce divide-by-zero
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
            return b.angle ();
        }

        public double absAngle ()
        {
            return b.absAngle ();
        }

        public String toString ()
        {
            return a + "->" + a.add (b);
        }
    }

    public static class Spline
    {
        Vector2 p0;
        Vector2 p1;
        Vector2 p2;
        Vector2 p3;

        public Spline (CubicCurve2D c)
        {
            p0 = new Vector2 (c.getX1     (), c.getY1     ());
            p1 = new Vector2 (c.getCtrlX1 (), c.getCtrlY1 ());
            p2 = new Vector2 (c.getCtrlX2 (), c.getCtrlY2 ());
            p3 = new Vector2 (c.getX2     (), c.getY2     ());
        }

        public Vector2 paramtetricPoint (double t)
        {
            double t1 = 1 - t;
            double a = t1 * t1 * t1;
            double b = 3 * t1 * t1 * t;
            double c = 3 * t1 * t * t;
            double d = t * t * t;
            double x = a * p0.x + b * p1.x + c * p2.x + d * p3.x;
            double y = a * p0.y + b * p1.y + c * p2.y + d * p3.y;
            return new Vector2 (x, y);
        }

        /**
            Finds the point where this segment and that segment intersect.
            Result is a number in [0,1]. If they do not intersect, result is infinity.
            Based on https://www.particleincell.com/2013/cubic-line-intersection
        **/
        public double intersection (Segment2 that)
        {
            // Line formula: Ax + By = C, where [A,B] is a vector perpendicular to the line.
            // Create perpendicular vector via clockwise rotation.
            double A =  that.b.y;
            double B = -that.b.x;
            double C =  that.a.x * A + that.a.y * B;

            double[] bx = coefficients (p0.x, p1.x, p2.x, p3.x);
            double[] by = coefficients (p0.y, p1.y, p2.y, p3.y);

            double[] P = new double[4];
            P[0] = A * bx[0] + B * by[0] - C; // t^0
            P[1] = A * bx[1] + B * by[1];     // t^1
            P[2] = A * bx[2] + B * by[2];     // t^2
            P[3] = A * bx[3] + B * by[3];     // t^3
            int rootCount = CubicCurve2D.solveCubic (P);

            // verify the roots are in bounds of the linear segment
            double result = Double.POSITIVE_INFINITY;
            for (int i = 0; i < rootCount; i++)
            {
                double t = P[i];
                if (t < 0  ||  t > 1) continue;

                // Intersection point on infinite line
                double x = bx[0] + t * (bx[1] + t * (bx[2] + t * bx[3]));
                double y = by[0] + t * (by[1] + t * (by[2] + t * by[3]));

                // Convert point to parametric form and check if in range
                double s = -1;
                if (Math.abs (that.b.y) > Math.abs (that.b.x)) s = (y - that.a.y) / that.b.y;
                else if (that.b.x != 0)                        s = (x - that.a.x) / that.b.x;
                if (s < 0  ||  s > 1) continue;
                result = Math.min (result, t);
            }

            return result;
        }

        public double[] coefficients (double P0, double P1, double P2, double P3)
        {
            double[] Z = new double[4];
            Z[0] =      P0;
            Z[1] = -3 * P0 + 3 * P1;
            Z[2] =  3 * P0 - 6 * P1 + 3 * P2;
            Z[3] =     -P0 + 3 * P1 - 3 * P2 + P3;
            return Z;
        }
    }
}
