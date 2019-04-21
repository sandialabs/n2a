/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

@SuppressWarnings("serial")
public class GraphNode extends JPanel
{
    public NodePart node;
    public JLabel   label;

    protected static RoundedBorder border = new RoundedBorder (5);

    public GraphNode (NodePart node)
    {
        this.node = node;
        MNode source = node.source;

        label = new JLabel (source.key ());

        Lay.BLtg (this, "C", label);
        setBorder (border);
        setOpaque (false);

        MNode gui = source.child ("$metadata", "gui", "bounds");
        if (gui != null)
        {
            int x = gui.getInt ("x");
            int y = gui.getInt ("y");
            setLocation (x, y);
        }

        MouseInputListener resizeListener = new MouseInputAdapter ()
        {
            int       cursor;
            Point     start = null;
            Dimension min;
            Rectangle old;

            public void mouseMoved (MouseEvent me)
            {
                if (start == null) setCursor (Cursor.getPredefinedCursor (border.getCursor (me)));
            }

            public void mouseExited (MouseEvent mouseEvent)
            {
                // It is possible to get this event in the middle of a drag, so ignore that case.
                if (start == null) setCursor (Cursor.getDefaultCursor ());
            }

            public void mousePressed (MouseEvent me)
            {
                if (PanelModel.instance.panelEquations.locked) return;

                // All mouse event coordinates are relative to the bounds of this component.
                start  = me.getPoint ();
                min    = getMinimumSize ();
                old    = getBounds ();
                cursor = border.getCursor (me);
                setCursor (Cursor.getPredefinedCursor (cursor));
            }

            public void mouseDragged (MouseEvent me)
            {
                if (start == null) return;

                int x = getX ();
                int y = getY ();
                int w = getWidth ();
                int h = getHeight ();
                int dx = me.getX () - start.x;
                int dy = me.getY () - start.y;

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
                        start.translate (dx, 0);
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
                        start.translate (0, dy);
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
                    case Cursor.MOVE_CURSOR:
                        animate (new Rectangle (x + dx, y + dy, w, h));
                }
            }

            public void mouseReleased (MouseEvent mouseEvent)
            {
                start = null;

                // Store new bounds in metadata
                // TODO: make this undoable
                Rectangle now = getBounds ();
                if (now.x      != old.x     ) node.source.set (now.x,      "$metadata", "gui", "bounds", "x");
                if (now.y      != old.y     ) node.source.set (now.y,      "$metadata", "gui", "bounds", "y");
                if (now.width  != old.width ) node.source.set (now.width,  "$metadata", "gui", "bounds", "width");
                if (now.height != old.height) node.source.set (now.height, "$metadata", "gui", "bounds", "height");
            }
        };
        addMouseListener (resizeListener);
        addMouseMotionListener (resizeListener);
    }

    public Dimension getPreferredSize ()
    {
        int w = 0;
        int h = 0;
        MNode gui = node.source.child ("$metadata", "gui", "bounds");
        if (gui != null)
        {
            w = gui.getInt ("width");
            h = gui.getInt ("height");
        }
        if (w != 0  &&  h != 0) return new Dimension (w, h);

        Dimension d = super.getPreferredSize ();  // Gets the layout manager's opinion.
        if (w != 0) d.width  = w;
        if (h != 0) d.height = h;
        return d;
    }

    /**
        Sets bounds and also repaints portion of container that was exposed by move or resize.
    **/
    public void animate (Rectangle next)
    {
        // Only setBounds() and validate() are strictly necessary, but do paintImmediately() just in case UI lags.
        Rectangle old = getBounds ();
        setBounds (next);
        validate ();
        PanelEquationGraph p = (PanelEquationGraph) getParent ();
        p.paintImmediately (old.union (next));
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

            Color color;
            GraphNode gn = (GraphNode) c;
            switch (gn.node.getForegroundColor ())
            {
                case NodeBase.OVERRIDE:
                    color = EquationTreeCellRenderer.colorOverride;
                    break;
                case NodeBase.KILL:
                    color = EquationTreeCellRenderer.colorKill;
                    break;
                default:  // INHERIT
                    color = EquationTreeCellRenderer.colorInherit;
            }
            g2.setPaint (color);
            g2.draw (border);

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
            }
            else if (x >= w - t)
            {
                if (y <  t    ) return Cursor.NE_RESIZE_CURSOR;
                if (y >= h - t) return Cursor.SE_RESIZE_CURSOR;
            }
            else
            {
                if (y >= t  &&  y < h - t) return Cursor.DEFAULT_CURSOR;
            }
            return Cursor.MOVE_CURSOR;
        }
    }
}
