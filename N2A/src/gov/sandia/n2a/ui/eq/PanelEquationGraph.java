/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Enumeration;
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
            if (c instanceof NodePart) add ("", new GraphNode ((NodePart) c));  // Necessary to specify string, to force use of LayoutManager.addLayoutComponent().
        }
        revalidate ();
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

        // TODO: Draw connection edges
    }

    public void updateUI ()
    {
        GraphNode.RoundedBorder.updateUI ();
        background = UIManager.getColor ("SplitPane.background");
    }

    public static class GraphLayout implements LayoutManager
    {
        public Rectangle bounds = new Rectangle ();
        public boolean   needSize;

        public void addLayoutComponent (String name, Component comp)
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

        public Dimension preferredLayoutSize (Container parent)
        {
            if (! needSize) return bounds.getSize ();
            bounds = new Rectangle ();
            int count = parent.getComponentCount ();
            for (int i = 0; i < count; i++)
            {
                Component c = parent.getComponent (i);
                Dimension d = c.getSize ();
                Point     p = c.getLocation ();
                bounds.union (new Rectangle (p, d));
            }
            return bounds.getSize ();
        }

        public Dimension minimumLayoutSize (Container parent)
        {
            return preferredLayoutSize (parent);
        }

        public void layoutContainer (Container parent)
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
