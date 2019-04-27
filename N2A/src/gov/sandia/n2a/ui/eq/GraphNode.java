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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;
import javax.swing.tree.TreePath;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.ChangeGUI;

@SuppressWarnings("serial")
public class GraphNode extends JPanel
{
    protected GraphPanel      parent;
    public    NodePart        node;
    public    JLabel          label;
    protected List<GraphEdge> edgesOut = new ArrayList<GraphEdge> ();
    protected List<GraphEdge> edgesIn  = new ArrayList<GraphEdge> ();
    protected boolean         needRevalidate;

    protected static RoundedBorder border = new RoundedBorder (5);

    public GraphNode (GraphPanel parent, NodePart node)
    {
        this.parent = parent;
        this.node   = node;
        node.graph  = this;

        label = new JLabel (node.source.key ());

        Lay.BLtg (this, "C", label);
        setBorder (border);
        setOpaque (false);

        MNode bounds = node.source.child ("$metadata", "gui", "bounds");
        if (bounds != null)
        {
            int x = bounds.getInt ("x") + parent.offset.x;
            int y = bounds.getInt ("y") + parent.offset.y;
            setLocation (x, y);
        }

        MouseInputListener resizeListener = new ResizeListener ();
        addMouseListener (resizeListener);
        addMouseMotionListener (resizeListener);
    }

    public Dimension getPreferredSize ()
    {
        int w = 0;
        int h = 0;
        MNode bounds = node.source.child ("$metadata", "gui", "bounds");
        if (bounds != null)
        {
            w = bounds.getInt ("width");
            h = bounds.getInt ("height");
        }
        if (w != 0  &&  h != 0) return new Dimension (w, h);

        Dimension d = super.getPreferredSize ();  // Gets the layout manager's opinion.
        d.width  = Math.max (d.width,  w);
        d.height = Math.max (d.height, h);
        return d;
    }

    /**
        Apply any changes from $metadata.
    **/
    public void updateGUI ()
    {
        MNode bounds = node.source.child ("$metadata", "gui", "bounds");
        if (bounds != null)
        {
            int x = bounds.getInt ("x") + parent.offset.x;
            int y = bounds.getInt ("y") + parent.offset.y;
            Dimension d = getPreferredSize ();
            animate (new Rectangle (x, y, d.width, d.height));
        }
    }

    /**
        Sets bounds and repaints portion of container that was exposed by move or resize.
    **/
    public void animate (Rectangle next)
    {
        Rectangle old = getBounds ();
        Rectangle paintRegion = next.union (old);
        setBounds (next);
        validate ();
        if (parent.layout.componentMoved (next, old)) needRevalidate = true;

        for (GraphEdge ge : edgesOut)
        {
            old = ge.bounds;
            paintRegion = paintRegion.union (old);
            ge.updateShape (this);
            paintRegion = paintRegion.union (ge.bounds);
            if (parent.layout.componentMoved (ge.bounds, old)) needRevalidate = true;
        }
        for (GraphEdge ge : edgesIn)
        {
            old = ge.bounds;
            paintRegion = paintRegion.union (old);
            ge.updateShape (this);
            paintRegion = paintRegion.union (ge.bounds);
            if (parent.layout.componentMoved (ge.bounds, old)) needRevalidate = true;
        }

        if (needRevalidate)
        {
            parent.revalidate ();
            needRevalidate = false;
        }
        parent.paintImmediately (paintRegion);
    }

    public class ResizeListener extends MouseInputAdapter implements ActionListener
    {
        int        cursor;
        Point      start;
        Dimension  min;
        Rectangle  old;
        MouseEvent lastEvent;
        Timer      timer = new Timer (100, this);

        public void mouseMoved (MouseEvent me)
        {
            if (start == null) setCursor (Cursor.getPredefinedCursor (border.getCursor (me)));
        }

        public void mouseExited (MouseEvent me)
        {
            // It is possible to get this event in the middle of a drag, so ignore that case.
            if (start == null) setCursor (Cursor.getDefaultCursor ());
        }

        public void mousePressed (MouseEvent me)
        {
            if (PanelModel.instance.panelEquations.locked) return;
            if (me.getButton () != MouseEvent.BUTTON1) return;

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

            JViewport vp = (JViewport) parent.getParent ();
            Point pp = vp.getLocationOnScreen ();
            Point pm = me.getLocationOnScreen ();
            pm.x -= pp.x;
            pm.y -= pp.y;
            Dimension extent = vp.getExtentSize ();
            boolean oob =  pm.x < 0  ||  pm.x > extent.width  ||  pm.y < 0  ||  pm.y > extent.height;
            if (! oob) timer.stop ();
            if (me == lastEvent)
            {
                if (! oob) return;

                // Rather than generate an actual mouse event, simply adjust (dx,dy).
                dx = pm.x < 0 ? pm.x : (pm.x > extent.width  ? pm.x - extent.width  : 0);
                dy = pm.y < 0 ? pm.y : (pm.y > extent.height ? pm.y - extent.height : 0);

                // Stretch bounds and shift viewport
                Rectangle old = getBounds ();
                Rectangle next = new Rectangle (old);
                next.translate (dx, dy);
                if (parent.layout.componentMoved (next, old)) needRevalidate = true;
                Point p = vp.getViewPosition ();
                p.translate (dx, dy);
                vp.setViewPosition (p);
            }
            else
            {
                if (oob)
                {
                    lastEvent = me;
                    if (timer.isRunning ()) timer.restart ();
                    else                    timer.start ();
                }
            }

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

        public void mouseReleased (MouseEvent me)
        {
            start = null;
            timer.stop ();
            if (me.getButton () != MouseEvent.BUTTON1) return;

            PanelModel mep = PanelModel.instance;
            if (cursor == Cursor.DEFAULT_CURSOR)  // Normal click
            {
                // Select node
                PanelEquationTree pet = mep.panelEquationTree;
                JTree tree = pet.tree;
                TreePath path = new TreePath (node.getPath ());
                tree.setSelectionPath (path);
                // The following lines are equivalent to JTree.scrollPathToVisible(path),
                // except that we expand the requested rectangle to force the node to the top of the frame.
                tree.makeVisible (path);
                Rectangle r = tree.getPathBounds (path);
                Rectangle visible = pet.getViewport ().getViewRect ();
                r.height = visible.height;
                tree.scrollRectToVisible (r);
            }
            else  // Move or resize (click on border)
            {
                // Store new bounds in metadata
                MNode guiTree = new MVolatile ();
                MNode bounds = guiTree.childOrCreate ("bounds");
                Rectangle now = getBounds ();
                if (now.x      != old.x     ) bounds.set (now.x - parent.offset.x, "x");
                if (now.y      != old.y     ) bounds.set (now.y - parent.offset.y, "y");
                if (now.width  != old.width ) bounds.set (now.width,               "width");
                if (now.height != old.height) bounds.set (now.height,              "height");
                if (bounds.size () > 0) mep.undoManager.add (new ChangeGUI (node, guiTree));
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
