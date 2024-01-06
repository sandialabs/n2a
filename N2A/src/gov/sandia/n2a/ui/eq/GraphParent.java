/*
Copyright 2019-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.event.MouseInputAdapter;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;

@SuppressWarnings("serial")
public class GraphParent extends JPanel
{
    protected PanelEquations    container;
    protected NodePart          part;
    public    PanelEquationTree panelEquationTree;
    protected ResizeListener    resizeListener = new ResizeListener ();

    protected static RoundedBottomBorder border = new RoundedBottomBorder (5);

    public GraphParent (PanelEquations container)
    {
        this.container = container;
        panelEquationTree = new PanelEquationTree (container, false);

        Lay.BLtg (this, "C", panelEquationTree);
        setBorder (border);
        setOpaque (false);
        setVisible (false);

        addMouseListener (resizeListener);
        addMouseMotionListener (resizeListener);
    }

    public void toggleOpen ()
    {
        boolean nextOpen = ! isVisible ();
        if (container.locked)
        {
            setOpen (nextOpen);
        }
        else
        {
            MNode metadata = new MVolatile ();
            metadata.set (nextOpen, "gui", "bounds", "parent");
            ChangeAnnotations ca = new ChangeAnnotations (part, metadata);
            ca.graph = true;
            ca.redo ();  // and don't record with undo manager.
        }
    }

    public void setOpen (boolean value)
    {
    	if (value == isVisible ()) return;
    	if (value)
        {
            setVisible (true);
            setSize (getPreferredSize ());
        }
        else
        {
            container.titleFocused = true;
            if (panelEquationTree.tree.isFocusOwner ()) container.breadcrumbRenderer.requestFocusInWindow ();
            setVisible (false);
        }
        boolean focused = container.breadcrumbRenderer.isFocusOwner ();
        boolean selected =  container.parentSelected  ||  focused;
        container.breadcrumbRenderer.getTreeCellRendererComponent (selected, focused);
    }

    /**
        Prepares the parent tree to display the current parent part.
        To simplify code in PanelEquations, we manage the parent tree, even if it is not the one held by this panel.
    **/
    public void loadPart ()
    {
        if (container.part == part) return;
        if (part != null) part.fakeRoot (false);
        part = container.part;
        part.fakeRoot (true);
        if (container.view == PanelEquations.NODE) panelEquationTree.loadPart (part);
        else                             container.panelEquationTree.loadPart (part);
        animate ();
    }

    /**
        Sets the parent tree to display nothing.
        To simplify code in PanelEquations, we manage the parent tree, even if it is not the one held by this panel.
    **/
    public void clear ()
    {
        if (container.view == PanelEquations.NODE) panelEquationTree.clear ();
        else                             container.panelEquationTree.clear ();
        if (part != null) part.fakeRoot (false);
        part = null;
    }

    public void takeFocus ()
    {
        panelEquationTree.takeFocus ();
    }

    public Dimension getPreferredSize ()
    {
        int w = 0;
        int h = 0;
        MNode boundsParent = part.source.child ("$meta", "gui", "bounds", "parent");
        if (boundsParent != null)
        {
            float em = SettingsLookAndFeel.em;
            w = (int) Math.round (boundsParent.getDouble ("width")  * em);
            h = (int) Math.round (boundsParent.getDouble ("height") * em);
            if (w != 0  &&  h != 0) return new Dimension (w, h);
        }

        Dimension d = super.getPreferredSize ();  // Gets the layout manager's opinion.
        d.width  = Math.max (d.width,  w);
        d.height = Math.max (d.height, h);

        // Don't exceed half of viewport.
        Dimension extent = container.panelEquationGraph.getViewport ().getExtentSize ();
        d.width  = Math.min (d.width,  extent.width  / 2);
        d.height = Math.min (d.height, extent.height / 2);

        return d;
    }

    public void updateGUI ()
    {
        boolean open = false;
        if (container.view == PanelEquations.NODE) open = part.source.getOrDefault (false, "$meta", "gui", "bounds", "parent");
        setOpen (open);
        animate ();
    }

    public void animate ()
    {
        if (! isVisible ()) return;
        Dimension next = getPreferredSize ();
        if (getSize () != next) animate (next);
    }

    /**
        Sets bounds and repaints portion of container that was exposed by move or resize.
    **/
    public void animate (Dimension next)
    {
        Rectangle old = getBounds ();
        setSize (next);
        Rectangle nextRegion = new Rectangle (old.getLocation (), next);
        Rectangle paintRegion = nextRegion.union (old);

        validate ();  // Preemptively redo internal layout, so this component will repaint correctly.
        ((JComponent) getParent ()).repaint (paintRegion);
    }

    public class ResizeListener extends MouseInputAdapter
    {
        int        cursor;
        Point      start;
        Dimension  min;
        Rectangle  old;

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
            start  = me.getPoint ();
            min    = getMinimumSize ();
            old    = getBounds ();
            cursor = border.getCursor (me);
            setCursor (Cursor.getPredefinedCursor (cursor));
        }

        public void mouseDragged (MouseEvent me)
        {
            if (start == null) return;

            int w = getWidth ();
            int h = getHeight ();
            int dx = me.getX () - start.x;
            int dy = me.getY () - start.y;

            switch (cursor)
            {
                case Cursor.E_RESIZE_CURSOR:
                    int newW = w + dx;
                    if (newW < min.width)
                    {
                        dx += min.width - newW;
                        newW = min.width;
                    }
                    animate (new Dimension (newW, h));
                    start.translate (dx, 0);
                    break;
                case Cursor.SE_RESIZE_CURSOR:
                    newW = w + dx;
                    if (newW < min.width)
                    {
                        dx += min.width - newW;
                        newW = min.width;
                    }
                    int newH = h + dy;
                    if (newH < min.height)
                    {
                        dy += min.height - newH;
                        newH = min.height;
                    }
                    animate (new Dimension (newW, newH));
                    start.translate (dx, dy);
                    break;
                case Cursor.S_RESIZE_CURSOR:
                    newH = h + dy;
                    if (newH < min.height)
                    {
                        dy += min.height - newH;
                        newH = min.height;
                    }
                    animate (new Dimension (w, newH));
                    start.translate (0, dy);
                    break;
            }
        }

        public void mouseReleased (MouseEvent me)
        {
            start = null;

            if (SwingUtilities.isLeftMouseButton (me))
            {
                if (cursor != Cursor.DEFAULT_CURSOR)  // Click on border
                {
                    // Store new bounds in metadata
                    MNode metadata = new MVolatile ();
                    MNode boundsParent = metadata.childOrCreate ("gui", "bounds", "parent");
                    Rectangle now = getBounds ();
                    float em = SettingsLookAndFeel.em;
                    if (now.width  != old.width ) boundsParent.setTruncated (now.width  / em, 2, "width");
                    if (now.height != old.height) boundsParent.setTruncated (now.height / em, 2, "height");
                    if (boundsParent.size () > 0) MainFrame.undoManager.apply (new ChangeAnnotations (part, metadata));
                }
            }

            takeFocus ();
        }
    }

    public static class RoundedBottomBorder extends AbstractBorder
    {
        public int t;

        protected static Color background = Color.white;

        RoundedBottomBorder (int thickness)
        {
            t = thickness;
        }

        public void paintBorder (Component c, Graphics g, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) g.create ();
            g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int t2 = t * 2;
            int offset = t2 + 2;  // Add enough pixels to ensure that rounded corners don't show at top.
            Shape border = new RoundRectangle2D.Double (x, y-offset, width-1, height-1+offset, t2, t2);

            g2.setPaint (background);
            g2.fill (border);

            GraphParent gp = (GraphParent) c;
            g2.setPaint (EquationTreeCellRenderer.getForegroundFor (gp.part, false));
            g2.draw (border);

            Shape line = new Line2D.Double (x, y, x+width-1, y);
            g2.draw (line);

            g2.dispose ();
        }

        public Insets getBorderInsets (Component c, Insets insets)
        {
            insets.top = 2;
            insets.left = insets.right = insets.bottom = t;
            return insets;
        }

        public static void updateUI ()
        {
            background = UIManager.getColor ("Tree.background");
        }

        public int getCursor (MouseEvent me)
        {
            if (! PanelModel.instance.panelEquations.enableResize) return Cursor.DEFAULT_CURSOR;

            int x = me.getX ();
            int y = me.getY ();
            Component c = me.getComponent ();
            int w = c.getWidth ();
            int h = c.getHeight ();

            if (x >= w - t)
            {
                if (y >= h - t) return Cursor.SE_RESIZE_CURSOR;
                return                 Cursor.E_RESIZE_CURSOR;
            }
            if (y >= h - t) return Cursor.S_RESIZE_CURSOR;
            return                 Cursor.DEFAULT_CURSOR;
        }
    }
}
