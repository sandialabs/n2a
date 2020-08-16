/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class GraphEdge
{
    public    GraphNode nodeFrom;    // The connection part. In the case of a pin-to-pin link, this can also be a peer part.
    public    GraphNode nodeTo;      // The endpoint that this edge goes to
    protected String    nameTo;      // Name of external (not in current graph) part that edge goes to. Will only be set if nodeTo is null. If both are null, this is an unconnected edge.
    protected GraphEdge edgeOther;   // For binary connections only, the edge to the other endpoint. Used to coordinate a smooth curve through the connection node.
    protected String    alias;       // Name of endpoint variable in connection part. Empty if this is a pin-to-pin link.
    protected String    topic;       // If this edge goes from a part to an IO block, then this gives the specific topic on that pin.
    protected String    text;        // Value to display on "from" side of edge. Includes both alias and pin topic, if any.
    protected String    pinSideTo;   // If attached to a pin, then which side the pin is on ("in" or "out"). null for a regular (non-pin) connection.
    public    String    pinKeyTo;    // If attached to a pin, then the key of the pin. null for regular connection.
    protected String    pinSideFrom; // For pin-to-pin link, this identifies the output pin.
    public    String    pinKeyFrom;  // ditto

    protected Shape     line;      // The edge itself, along with any additional strokes to paint the arrowhead, if its shape is open.
    protected Shape     head;      // A closed shape. May be null.
    protected boolean   headFill;  // if true, fill black; if false, fill with background (or white)
    protected Vector2   label;
    protected Rectangle textBox;
    protected Vector2   labelTo;
    protected Rectangle textBoxTo;
    protected Rectangle bounds = new Rectangle (0, 0, -1, -1);  // empty. Allows call to animate() on brand-new edges that have not previously called updateShape().
    protected Vector2   root;
    protected Vector2   tip;
    protected boolean   tipDrag;
    protected Point     anchor;  // When non-null, use this as start for tip drag. Value is relative to the upper-left corner of nodeFrom. Mainly for aesthetics.

    protected static double arrowheadAngle  = Math.PI / 5;
    protected static double arrowheadLength = 10;
    protected static float  strokeThickness = 3;
    protected static int    padNameTop      = 1;
    protected static int    padNameSide     = 2;
    protected static int    padNameBetween  = 10;

    public GraphEdge (GraphNode nodeFrom, NodePart partTo, String alias)
    {
        this.nodeFrom = nodeFrom;
        this.alias    = alias;
        topic         = "";

        if (partTo == null)
        {
            MNode pin = nodeFrom.node.source.child ("$metadata", "gui", "pin");
            if (pin == null) return;  // Simply an unconnected edge
            if (nodeFrom.node.connectionBindings == null) return;

            // Determine order of unbound endpoint(s)
            String first = null;
            String second = null;
            for (Entry<String,NodePart> e : nodeFrom.node.connectionBindings.entrySet ())
            {
                if (e.getValue () != null) continue;
                if      (first  == null) first  = e.getKey ();
                else if (second == null) second = e.getKey ();
                else break;
            }

            // Determine which IO block this edge goes to.
            String pinName = pin.getOrDefault (nodeFrom.node.source.key ());
            if (alias.equals (first))
            {
                nodeTo    = nodeFrom.container.panelEquationGraph.graphPanel.pinIn;
                pinSideTo = "out";
                pinKeyTo  = pinName;
                topic     = pin.getOrDefault ("data", "topic");
                nodeTo.node.pinOut.set ("", pinKeyTo, "bound");
            }
            else if (alias.equals (second)  &&  pin.child ("pass") != null)
            {
                nodeTo    = nodeFrom.container.panelEquationGraph.graphPanel.pinOut;
                pinSideTo = "in";
                pinKeyTo  = pin.getOrDefault (pinName, "pass");
                // Don't repeat topic on the output side. It would be unnecessary clutter.
                nodeTo.node.pinIn.set ("", pinKeyTo, "bound");
            }
            return;
        }

        // See if partTo or any of its parents is a graph node. If so, that should be the endpoint of this edge.
        NodePart p = partTo;
        while (p != null)
        {
            if (p.graph != null)
            {
                nodeTo = p.graph;  // Found the graph node that is the endpoint of this edge.
                break;
            }
            p = (NodePart) p.getParent ();
        }
        if (nodeTo == null)  // partTo is outside the current container.
        {
            NodeVariable v = (NodeVariable) nodeFrom.node.child (alias);
            nameTo = v.source.get ();
        }
        else  // partTo is inside the current container. Now check if the connection is to a pin.
        {
            NodePart partFrom = nodeFrom.node;  // The connection part
            String pinName = partFrom.source.get (alias, "$metadata", "gui", "pin");
            if (pinName.isEmpty ()) return;  // absent
            String[] pieces = pinName.split ("\\.", 2);
            if (pieces.length != 2) return;  // malformed
            String inout = pieces[0];
            String key   = pieces[1];
            if (nodeTo.node.source.child ("$metadata", "gui", "pin", inout, key) == null) return;  // pin doesn't exist
            pinSideTo = inout;
            pinKeyTo  = key;
            if (inout.equals ("in")) nodeTo.node.pinIn .set ("", key, "bound");
            else                     nodeTo.node.pinOut.set ("", key, "bound");
        }
    }

    /**
        For pin-to-pin link.
        @param nodeTo If null, then this is a drag edge. In this case, pinSize & pinKey refers to the pin on the "from" side.
        If non-null, then this is a completed edge. In this case, nodeTo must always be an input pin.
        pinKey refers to the pin on the "to" side, and the pin metadata will provide the identity of the
        pin on the "from" side. pinSide is redundant, since it must always be "in".
        If pinSide is "", then this is an output pin that needs to be visually linked to the IO block.
        In this case, the output node have a pin to connect, so the IO block cannot refer back to it.
        Instead, we treat this like a unary connection that is bound to a pin.
    **/
    public GraphEdge (GraphNode nodeFrom, GraphNode nodeTo, String pinSide, String pinKey)
    {
        this.nodeFrom = nodeFrom;
        this.nodeTo   = nodeTo;
        alias = "";
        topic = "";

        if (nodeTo == null)  // drag edge
        {
            pinSideFrom = pinSide;
            pinKeyFrom  = pinKey;
        }
        else if (pinSide.equals ("in"))  // pin-to-pin connection
        {
            pinSideTo   = "in";  // "pinSide" is ignored in this case
            pinKeyTo    = pinKey;
            pinSideFrom = "out";
            pinKeyFrom  = nodeTo.node.pinIn.get (pinKey, "bind", "pin");

            nodeFrom.node.pinOut.set ("", pinKeyFrom, "bound");
            nodeTo  .node.pinIn .set ("", pinKey,     "bound");
        }
        else  // output
        {
            pinSideTo = "in";
            pinKeyTo  = pinKey;
            // pinSideFrom and pinKeyFrom are null, just like a connector. The distinction is that alias is empty.
            topic = nodeFrom.node.source.getOrDefault ("data", "$metadata", "gui", "pin", "topic");

            nodeTo.node.pinIn.set ("", pinKey, "bound");
        }
    }

    public void clearBound ()
    {
        if (pinKeyFrom != null)
        {
            if (pinSideFrom.equals ("in")) nodeFrom.node.pinIn .clear (pinKeyFrom, "bound");
            else                           nodeFrom.node.pinOut.clear (pinKeyFrom, "bound");
        }
        if (pinKeyTo != null)
        {
            if (pinSideTo.equals ("in")) nodeTo.node.pinIn .clear (pinKeyTo, "bound");
            else                         nodeTo.node.pinOut.clear (pinKeyTo, "bound");
        }
    }

    /**
        Prepare edge for dragging when the anchor needs to be on an input pin.
        Drag is always on the "to" side of the edge. However, a finished pin-to-pin edge
        is always from output to input. In order to show the drag, the edge must be
        temporarily reconfigured to go in the opposite direction, so the correct end is
        free to drag.
    **/
    public void reversePins ()
    {
        GraphNode tempNode = nodeFrom;
        String    tempKey  = pinKeyFrom;
        String    tempSide = pinSideFrom;
        nodeFrom    = nodeTo;
        pinKeyFrom  = pinKeyTo;
        pinSideFrom = pinSideTo;
        nodeTo    = tempNode;
        pinKeyTo  = tempKey;
        pinSideTo = tempSide;
        // Must call updateShape() to fully implement the changes.
    }

    /**
        Updates our cached shape information based on current state of graph nodes.
    **/
    public void updateShape (boolean updateOther)
    {
        line       = null;
        head       = null;
        label      = null;
        textBoxTo  = null;
        bounds     = new Rectangle (0, 0, -1, -1);  // empty, so won't affect union(), and will return false from intersects()
        root       = null;

        int padTip = 0;  // Distance from boundary of nodeTo to target the tip. Varies depending on arrow type.
        String headType = nodeFrom.node.source.get (alias, "$metadata", "gui", "arrow");
        boolean straight = nodeFrom.node.source.getFlag (alias, "$metadata", "gui", "arrow", "straight");
        switch (headType)
        {
            case "circle":
            case "circleFill":
                padTip = (int) Math.round (arrowheadLength / 2);
        }

        Rectangle Cbounds = nodeFrom.getBounds ();
        Vector2 c = new Vector2 (Cbounds.getCenterX (), Cbounds.getCenterY ());

        Rectangle   Abounds    = null;
        Vector2     a          = null;
        double      tipAngle   = 0;
        Vector2     tipAway    = null;
        FontMetrics fm         = nodeFrom.getFontMetrics (nodeFrom.getFont ());
        int         lineHeight = fm.getHeight () + 2 * padNameTop;
        int         boxSize    = lineHeight / 2;
        if (tipDrag)
        {
            a = tip;
            if (anchor != null) c = new Vector2 (Cbounds.x + anchor.x, Cbounds.y + anchor.y);
        }
        else if (nodeTo != null)
        {
            Abounds = nodeTo.getBounds ();
            if (pinKeyTo == null)
            {
                a = new Vector2 (Abounds.getCenterX (), Abounds.getCenterY ());
                Abounds.grow (padTip, padTip);
            }
            else
            {
                Abounds.grow (padTip, 0);

                // Determine tip position and angle
                //   Determine vertical position down side of part.
                int y = Abounds.y + GraphNode.border.t + boxSize;  // vertical center of first pin
                //   Determine horizontal position and tip angle
                if (pinSideTo.equals ("in"))
                {
                    y += lineHeight * nodeTo.node.pinIn.getInt (pinKeyTo, "order");
                    tip = new Vector2 (Abounds.x - boxSize - 1, y);
                    tipAngle = Math.PI;
                    tipAway = new Vector2 (-1, 0);
                }
                else  // out
                {
                    y += lineHeight * nodeTo.node.pinOut.getInt (pinKeyTo, "order");
                    tip = new Vector2 (Abounds.x + Abounds.width + boxSize, y);
                    tipAngle = 0;
                    tipAway = new Vector2 (1, 0);
                }

                a = tip;
            }
        }

        Vector2 ba = null;  // Non-null for binary connections that also need a curve rather than straight line.
        Vector2 c2c = null;
        if (! straight  &&  nodeTo != null  &&  edgeOther != null  &&  edgeOther.nodeTo != null)  // curve passing through a connection
        {
            Vector2 b;
            double flip = 0;  // Similar role to tipAway, but for edgeOther.
            if (edgeOther.tipDrag)
            {
                b = edgeOther.tip;
            }
            else
            {
                Rectangle Bbounds = edgeOther.nodeTo.getBounds ();
                if (edgeOther.pinKeyTo == null)
                {
                    b = new Vector2 (Bbounds.getCenterX (), Bbounds.getCenterY ());
                }
                else
                {
                    Bbounds.grow (padTip, 0);
                    int y = Bbounds.y + GraphNode.border.t + boxSize;
                    if (edgeOther.pinSideTo.equals ("in"))
                    {
                        y += lineHeight * edgeOther.nodeTo.node.pinIn.getInt (edgeOther.pinKeyTo, "order");
                        b = new Vector2 (Bbounds.x - boxSize, y);
                        flip = -1;
                    }
                    else
                    {
                        y += lineHeight * edgeOther.nodeTo.node.pinOut.getInt (edgeOther.pinKeyTo, "order");
                        b = new Vector2 (Bbounds.x + Bbounds.width + boxSize, y);
                        flip = 1;
                    }
                }
            }

            ba = a.subtract (b);  // vector from b -> a
            Vector2 avg = a.add (b).multiply (0.5);  // average position
            c2c = c.subtract (avg);
            double c2cLength = c2c.length ();
            double baLength = ba.length ();
            if (c2cLength > baLength)
            {
                c2c = c2c.normalize ();
                if (baLength > 0)
                {
                    ba = ba.normalize ();
                }
                else  // Both A and B nodes are at exactly the same place. Create vector perpendicular to c2c.
                {
                    ba = new Vector2 (-c2c.y, c2c.x);  // Vector to left side of c2c.

                    // The "A" edge goes to the right side of c2c, while "B" goes to the left.
                    if (nodeFrom.edgesOut.get (0) == this) ba = ba.multiply (-1);

                    if (pinSideTo == null)  // We are an ordinary connection.
                    {
                        // If other edge is a pin connection, go away from it.
                        if (ba.x * flip > 0) ba = ba.multiply (-1);
                    }
                    else  // We connect to a pin.
                    {
                        // Follow our own pin direction.
                        if (ba.x * tipAway.x < 0) ba = ba.multiply (-1);
                    }
                }
            }
            else  // c2cLength <= baLength; That is, c is roughly between a and b.
            {
                if (baLength < 10  ||  c2cLength < 10)  // Nodes are too close to compute good angles.
                {
                    ba = null;  // Draw straight lines. In this case, neither ba nor c2c will be used.
                }
                else
                {
                    c2c = c2c.normalize ();
                    ba = ba.normalize ();

                    // Interpolate between c2c and ac (vector from a to c).
                    double r = c2cLength / baLength;
                    Vector2 ac = c.subtract (a).normalize ();
                    c2c = c2c.multiply (r).add (ac.multiply (1 - r)).normalize ();
                }
            }

            // If needed, update shape parameters of the other edge.
            // The two possible sources of a call are nodeFrom and nodeTo.
            // If nodeTo, then we propagate the update to our peer edge.
            // However, if this is a self-connection, then we are our peer edge, so don't start an infinite loop.
            if (updateOther) edgeOther.updateShape (false);
        }

        if (topic.isEmpty ()) text = alias;
        else                  text = alias + "(" + topic + ")";
        double tw = fm.stringWidth (text);
        double th = fm.getHeight ();
        double nodeAngle = Math.atan ((double) Cbounds.height / Cbounds.width);

        if (nameTo != null)  // External endpoint
        {
            if (tipDrag)
            {
                root = intersection (new Segment2 (c, tip), Cbounds);
                if (root == null) return;  // tip is inside Cbounds
                line = new Line2D.Double (c.x, c.y, tip.x, tip.y);
                tipAngle = new Segment2 (tip, c).angle ();
            }
            else
            {
                // Determine offset
                List<Integer> widths = new ArrayList<Integer> ();
                int totalWidth = 0;
                int index = 0;
                for (String key : nodeFrom.node.connectionBindings.keySet ())
                {
                    // Select only connection bindings that require resolution up to container.
                    // Ignore unconnected edges and edges to children of siblings.
                    NodePart p = nodeFrom.node.connectionBindings.get (key);
                    if (p == null) continue;  // not connected
                    boolean hasGraph = false;  // detect siblings
                    while (p != null)
                    {
                        if (p.graph != null)
                        {
                            hasGraph = true;
                            break;
                        }
                        p = (NodePart) p.getParent ();
                    }
                    if (hasGraph) continue;

                    if (key.equals (alias)) index = widths.size ();
                    NodeVariable v = (NodeVariable) nodeFrom.node.child (key);
                    int width = fm.stringWidth (v.source.get ());
                    widths.add (width);
                    totalWidth += width;
                }
                double offset = 0;
                int count = widths.size ();
                if (count > 1)
                {
                    totalWidth += (count - 1) * padNameBetween;  // pad between each label
                    for (int i = 0; i < index; i++) offset += widths.get (i) + padNameBetween;  // Add widths and padding for all labels that precede this one.
                    offset += widths.get (index) / 2.0;  // Add half of this label, to reach its center.
                    offset -= totalWidth / 2.0;  // Offset to center the whole thing over the node.
                }
                int x = (int) (c.x + offset);

                // Determine text box. Need text height to locate arrowhead, so might as well calculate it all now.
                double ew = fm.stringWidth (nameTo);
                double eh = fm.getHeight ();
                labelTo = new Vector2 (x - ew / 2, Cbounds.y - 3 * eh - 2 * arrowheadLength);

                textBoxTo = new Rectangle ();
                textBoxTo.x      = (int) labelTo.x - padNameSide;
                textBoxTo.y      = (int) labelTo.y - padNameTop;
                textBoxTo.width  = (int) Math.ceil (ew) + 2 * padNameSide;
                textBoxTo.height = (int) Math.ceil (eh) + 2 * padNameTop;

                bounds = bounds.union (textBoxTo);
                labelTo.y += fm.getAscent ();

                tip = new Vector2 (x, textBoxTo.y + textBoxTo.height + padTip);

                Vector2 w1 = tip.add (new Vector2 (0, Cbounds.y - tip.y));
                Vector2 w2 = c.add (new Vector2 (offset, 0));
                line = new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, w2.x, w2.y, c.x, c.y);

                Spline spline = new Spline ((CubicCurve2D) line);
                root = intersection (spline, Cbounds);  // on boundary of c
                if (root == null) return;   // This should never happen in current arrangement, since target is at fixed distance above us.
                tipAngle = Math.PI / 2;
            }
        }
        else if (alias.isEmpty ()  &&  (pinKeyFrom != null  ||  pinKeyTo != null))  // pin-to-pin, output-to-pin, or dragging from pin
        {
            if (pinKeyFrom == null)
            {
                c = new Vector2 (Cbounds.x + Cbounds.width, Cbounds.y + Cbounds.height / 2);
                ba = new Vector2 (1, 0);
            }
            else
            {
                int y = Cbounds.y + GraphNode.border.t + boxSize;
                if (pinSideFrom.equals ("in"))
                {
                    y += lineHeight * nodeFrom.node.pinIn.getInt (pinKeyFrom, "order");
                    c = new Vector2 (Cbounds.x - boxSize, y);
                    ba = new Vector2 (-1, 0);
                }
                else  // pinSideFrom is "out"
                {
                    y += lineHeight * nodeFrom.node.pinOut.getInt (pinKeyFrom, "order");
                    c = new Vector2 (Cbounds.x + Cbounds.width + boxSize, y);
                    ba = new Vector2 (1, 0);
                }
            }

            root = c;
            if (tipDrag  ||  nodeTo != nodeFrom)  // Usual case: single segment between two separate parts (or part to drag-tip).
            {
                Vector2 tc = c.subtract (tip);
                double length = tc.length () / 3;
                if (tipDrag) tipAway = tc.normalize ();
                Vector2 w1 = tip.add (tipAway.multiply (length));
                Vector2 w2 = c.add (ba.multiply (length));
                line = new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, w2.x, w2.y, c.x, c.y);
            }
            else  // Self connection, so create two segments to wrap around top of part.
            {
                // The central point will act as endpoint to both segments.
                int order = nodeTo.node.pinIn.getInt (pinKeyTo, "order");
                Rectangle bounds = nodeTo.getBounds ();
                int halfWidth = bounds.width / 2;
                Vector2 m = new Vector2 (bounds.x + halfWidth, bounds.y - (lineHeight * (order + 1)));
                double length  = bounds.width / 3 + lineHeight * order * 1.5;  // The fudge factor at the end is to make the vertical run of the Bezier curves appear to have the same spacing as the horizontal run across the top. It is arbitrary and subjective.
                double length2 = halfWidth + boxSize + length;

                Vector2 w1 = tip.add (tipAway.multiply (length));
                Vector2 w2 = c.add (ba.multiply (length));
                Vector2 m1 = new Vector2 (m.x - length2, m.y);
                Vector2 m2 = new Vector2 (m.x + length2, m.y);

                Path2D path = new Path2D.Double ();
                path.append (new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, m1.x, m1.y, m.x, m.y), false);
                path.append (new CubicCurve2D.Double (m.x, m.y, m2.x, m2.y, w2.x, w2.y, c.x, c.y), false);
                line = path;
            }
        }
        else if (nodeTo == null)  // Unconnected endpoint
        {
            if (! tipDrag)
            {
                // Distribute rays around node. This method is limited to 8 positions,
                // but no sane person would have more than an 8-way connection.
                int count = nodeFrom.edgesOut.size ();
                int index = nodeFrom.edgesOut.indexOf (this);
                double angle = Math.PI + index * Math.PI * 2 / count;
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
            if (root == null) return;  // tip is inside Cbounds
            line = new Line2D.Double (c.x, c.y, tip.x, tip.y);
            tipAngle = new Segment2 (tip, c).angle ();
        }
        else if (pinKeyTo != null  &&  ! tipDrag)  // from connection to pin. (If dragging, then handled by other cases below.)
        {
            Vector2 ct = tip.subtract (c);
            double length = ct.length () / 3;
            tipAway = tipAway.multiply (length);
            Vector2 w1 = tip.add (tipAway);

            if (ba == null)  // Straight line from C to A
            {
                Vector2 w2 = c.add (ct.divide (3));
                line = new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, w2.x, w2.y, c.x, c.y);

                Segment2 s = new Segment2 (a, c);
                root = intersection (s, Cbounds);  // root can be null if a is inside Cbounds
            }
            else  // Curve passing through C then toward A
            {
                Vector2 w2 = c.add (ba.multiply (length));
                line = new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, w2.x, w2.y, c.x, c.y);

                Spline spline = new Spline ((CubicCurve2D) line);
                root = intersection (spline, Cbounds);  // on boundary of c
            }
            if (root == null) return;
        }
        else if (ba == null)  // Draw straight line.
        {
            Segment2 s = new Segment2 (a, c);
            if (! tipDrag) tip = intersection (s, Abounds);  // tip can be null if c is inside Abounds
            root = intersection (s, Cbounds);  // root can be null if a is inside Cbounds
            if (tip == null  ||  root == null) return;
            line = new Line2D.Double (c.x, c.y, tip.x, tip.y);
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
            line = new CubicCurve2D.Double (tip.x, tip.y, w1.x, w1.y, w2.x, w2.y, c.x, c.y);

            Spline spline = new Spline ((CubicCurve2D) line);
            root = intersection (spline, Cbounds);  // on boundary of c
            if (root == null) return;
        }

        // Arrow head
        double ah = arrowheadLength / 2;  // arrowhead half-width
        switch (headType)
        {
            case "arrow":
                Path2D path = new Path2D.Double (line);  // Wrap shape in path object so we can extend it
                Vector2 end = new Vector2 (tip, tipAngle + arrowheadAngle, arrowheadLength);
                path.append (new Line2D.Double (end.x, end.y, tip.x, tip.y), false);
                end = new Vector2 (tip, tipAngle - arrowheadAngle, arrowheadLength);
                path.append (new Line2D.Double (tip.x, tip.y, end.x, end.y), false);
                line = path;
                break;
            case "circle":
                head = new Ellipse2D.Double (tip.x - ah, tip.y - ah, arrowheadLength, arrowheadLength);
                headFill = false;
                break;
            case "circleFill":
                head = new Ellipse2D.Double (tip.x - ah, tip.y - ah, arrowheadLength, arrowheadLength);
                headFill = true;
                break;
        }

        bounds = bounds.union (line.getBounds ());
        if (head != null) bounds = bounds.union (head.getBounds ());
        int t = (int) Math.ceil (strokeThickness / 2);
        bounds.grow (t, t);

        // Name
        if (! text.isEmpty ())
        {
            label = new Vector2 (0, 0);
            double absAngle = root.subtract (c).absAngle ();
            if (absAngle > nodeAngle)  // top or bottom
            {
                label.x = root.x - tw / 2;
                if (root.y < c.y) label.y = root.y - th - padNameTop;
                else              label.y = root.y      + padNameTop;
            }
            else  // left or right
            {
                if (root.x < c.x) label.x = root.x - tw - padNameSide;
                else              label.x = root.x      + padNameSide;
                label.y = root.y - th / 2;
            }

            textBox = new Rectangle ();
            textBox.x      = (int) label.x - padNameSide;
            textBox.y      = (int) label.y - padNameTop;
            textBox.width  = (int) Math.ceil (tw) + 2 * padNameSide;
            textBox.height = (int) Math.ceil (th) + 2 * padNameTop;
            bounds = bounds.union (textBox);
            label.y += fm.getAscent ();
        }
    }

    /**
        Not a true paintComponent(), but rather a subroutine of PanelEquationGraph.paintComponent().
        We get called with Graphics g already configured with our stroke width and other settings.
        To maximize efficiency, we don't make a local copy of g.
        g gets disposed by the caller immediately after all GraphEdges are done drawing, so any
        changes we make to g are safe.
    **/
    public void paintComponent (Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g;

        g2.setColor (Color.black);
        if (line != null) g2.draw (line);
        if (head != null)
        {
            if (! headFill) g2.setColor (PanelEquationGraph.background);
            g2.fill (head);
            g2.setColor (Color.black);
            g2.draw (head);
        }

        if (! text.isEmpty ()  &&  label != null)
        {
            g2.setColor (new Color (0xD0FFFFFF, true));
            g2.fill (textBox);
            g2.setColor (Color.black);
            g2.drawString (text, (float) label.x, (float) label.y);
        }

        if (textBoxTo != null)
        {
            g2.setColor (new Color (0xD0FFFFFF, true));
            g2.fill (textBoxTo);
            g2.setColor (Color.black);
            g2.drawString (nameTo, (float) labelTo.x, (float) labelTo.y);
        }
    }

    /**
        Create icons for context menu.
        Icons should be regenerated any time the look & feel changes.
    **/
    public static Icon iconFor (String headType)
    {
        int h = (int) Math.ceil (arrowheadLength + strokeThickness);
        int w = 2 * h;
        int y = h / 2;
        BufferedImage result = new BufferedImage (w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics ();
        g.setBackground (new Color (0, 0, 0, 1));
        g.clearRect (0, 0, w, h);

        // line
        int pad = (int) Math.ceil (strokeThickness / 2);
        int padTip = pad;
        switch (headType)
        {
            case "circle":
            case "circleFill":
                padTip += (int) Math.round (arrowheadLength / 2);
        }
        g.setStroke (new BasicStroke (strokeThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor (Color.black);
        g.draw (new Line2D.Double (padTip, y, w, y));

        // head
        double ah = arrowheadLength / 2;
        switch (headType)
        {
            case "arrow":
                Vector2 tip = new Vector2 (padTip, y);  // Hides member variable.
                Vector2 end = new Vector2 (tip, arrowheadAngle, arrowheadLength);
                g.draw (new Line2D.Double (end.x, end.y, tip.x, tip.y));
                end = new Vector2 (tip, -arrowheadAngle, arrowheadLength);
                g.draw (new Line2D.Double (tip.x, tip.y, end.x, end.y));
                break;
            case "circle":
                Shape circle = new Ellipse2D.Double (pad, y - ah, arrowheadLength, arrowheadLength);
                g.setColor (PanelEquationGraph.background);
                g.fill (circle);
                g.setColor (Color.black);
                g.draw (circle);
                break;
            case "circleFill":
                circle = new Ellipse2D.Double (pad, y - ah, arrowheadLength, arrowheadLength);
                g.fill (circle);
                g.draw (circle);
                break;
        }

        return new ImageIcon (result);
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
