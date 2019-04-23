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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JPanel;
import javax.swing.UIManager;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.eq.tree.NodePart;

@SuppressWarnings("serial")
public class PanelEquationGraph extends JPanel
{
    protected PanelEquations  container;
    protected GraphLayout     layout;
    protected boolean         needLayout;  // Set by load() if all nodes lack location information.
    protected List<GraphEdge> edges = new ArrayList<GraphEdge> (); // Note that GraphNodes are stored directly as Swing components.

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
        edges.clear ();

        Enumeration<?> children = container.root.children ();
        needLayout = children.hasMoreElements ();
        while (children.hasMoreElements ())
        {
            Object c = children.nextElement ();
            if (c instanceof NodePart)
            {
                GraphNode gn = new GraphNode ((NodePart) c);
                add (gn);
                if (gn.getX () != 0  ||  gn.getY () != 0) needLayout = false;
            }
        }

        // Scan children to set up connections
        for (Component c : getComponents ())
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

            if (! needLayout) for (GraphEdge ge : gn.edgesOut) ge.updateShape (gn);
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
        g2.setStroke (new BasicStroke (GraphEdge.strokeThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (GraphEdge e : edges)
        {
            if (e.bounds != null  &&  e.bounds.intersects (clip)) e.paintComponent (g2);
        }

        g2.dispose ();
    }

    public void updateUI ()
    {
        GraphNode.RoundedBorder.updateUI ();
        background = UIManager.getColor ("SplitPane.background");
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
            PanelEquationGraph peg = (PanelEquationGraph) target;
            System.out.println ("layoutContainer " + peg.needLayout);
            if (! peg.needLayout) return;

            // TODO: use potential-field method, such as "Drawing Graphs Nicely Using Simulated Annealing" by Davidson & Harel (1996).

            // For now, a very simple layout. Arrange in a grid with some space between nodes.
            Component[] components = target.getComponents ();
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

            // Apply changes to edges
            for (int i = 0; i < components.length; i++)
            {
                GraphNode gn = (GraphNode) components[i];
                for (GraphEdge ge : gn.edgesOut) ge.updateShape (gn);
            }

            // TODO: the equation tree should be rebuilt, so that new metadata nodes become visible.

            peg.needLayout = false;
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
