/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.Painter;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.tree.TreeCellRenderer;

import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

/**
    Complete implementation of TreeCellRenderer that works with our NodeBase.
    This is the core code that makes NodeBase work as a tree node representation.
**/
@SuppressWarnings("serial")
public class EquationTreeCellRenderer extends JPanel implements TreeCellRenderer
{
    protected LayoutManager2 layout;
    protected JLabel         iconHolder = new JLabel ();
    protected JLabel         label      = new JLabel ();             // The first text column, for convenient access.
    protected List<JLabel>   labels     = new ArrayList<JLabel> ();  // All text columns, including the first.

    protected boolean selected;
    protected boolean hasFocus;
    protected boolean isDropCell;
    protected boolean nontree;  // Need hack to paint background.
    protected boolean bigIcon;  // OK to use icons larger than 16x16.
    protected boolean hideIcon; // Completely suppress the icon for this node.

    // These colors may get changed when look & feel is changed.
    public static Color  colorInherit          = Color.blue;
    public static Color  colorOverride         = Color.black;
    public static Color  colorKill             = Color.red;
    public static Color  colorSelectedInherit  = Color.blue;
    public static Color  colorSelectedOverride = Color.black;
    public static Color  colorSelectedKill     = Color.red;
    public static String colorHighlight;  // Color name, so it can be used in HTML.
    public static String leftArrow = "🡰";

    public static final float lightThreshold = 0.5f;  // The dividing value between foreground brightnesses considered "dark" and those considered "light".

    protected static Icon    iconClosed;
    protected static Icon    iconOpen;
    protected static Icon    iconLeaf;
    protected static boolean fillBackground;
    protected static boolean drawsFocusBorderAroundIcon;
    protected static boolean drawDashedFocusIndicator;
    protected static Color   colorBackground;
    protected static Color   colorBackgroundSelected;
    protected static Color   colorBackgroundDropCell;
    protected static Color   colorBorderSelected;
    //protected static Color   colorDropCell;

    protected static Painter<JComponent> backgroundFocused;
    protected static Painter<JComponent> backgroundSelected;
    protected static Painter<JComponent> backgroundFocusedSelected;

    public EquationTreeCellRenderer ()
    {
        setLayout (new ColumnLayout ());
        setOpaque (false);

        add (iconHolder);
        add (Box.createHorizontalStrut (iconHolder.getIconTextGap ()));
        add (label);

        labels.add (label);
    }

    @SuppressWarnings("unchecked")
    public static void staticUpdateUI ()
    {
        // Check colors to see if text is dark or light.
        Color fg = UIManager.getColor ("Tree.textForeground");
        float[] hsl = Utility.HSLfromColor (fg);
        if (hsl[2] > lightThreshold)  // Light text
        {
            colorInherit  = new Color (0xC0C0FF);  // light blue
            colorOverride = Color.white;
            colorKill     = Color.pink;
            colorHighlight = "#200800";  // dark orange. Fallback value in case colorBackground is not available for reference below.
        }
        else  // Dark text
        {
            colorInherit  = Color.blue;
            colorOverride = Color.black;
            colorKill     = Color.red;
            colorHighlight = "#FFE8B0";  // light orange. Fallback.
        }

        fg = UIManager.getColor ("Tree.selectionForeground");
        hsl = Utility.HSLfromColor (fg);
        if (hsl[2] > lightThreshold)
        {
            colorSelectedInherit  = new Color (0xC0C0FF);
            colorSelectedOverride = Color.white;
            colorSelectedKill     = Color.pink;
        }
        else  // Dark text
        {
            colorSelectedInherit  = Color.blue;
            colorSelectedOverride = Color.black;
            colorSelectedKill     = Color.red;
        }

        iconLeaf   = UIManager.getIcon ("Tree.leafIcon");
        iconClosed = UIManager.getIcon ("Tree.closedIcon");
        iconOpen   = UIManager.getIcon ("Tree.openIcon");

        fillBackground = true;
        Object o = UIManager.get ("Tree.rendererFillBackground");
        if (o instanceof Boolean) fillBackground = (Boolean) o;

        drawsFocusBorderAroundIcon = UIManager.getBoolean ("Tree.drawsFocusBorderAroundIcon");  // Default should be false, so blind read is OK.
        drawDashedFocusIndicator   = UIManager.getBoolean ("Tree.drawDashedFocusIndicator");    // ditto
        colorBackground            = UIManager.getColor   ("Tree.textBackground");
        colorBackgroundSelected    = UIManager.getColor   ("Tree.selectionBackground");
        colorBackgroundDropCell    = UIManager.getColor   ("Tree.dropCellBackground");
        colorBorderSelected        = UIManager.getColor   ("Tree.selectionBorderColor");
        //colorDropCell              = UIManager.getColor   ("Tree.dropCellForeground");

        if (colorBackground != null)
        {
            float[] hsb = new float[3];
            Color.RGBtoHSB (colorBackground.getRed (), colorBackground.getGreen (), colorBackground.getBlue (), hsb);
            hsb[0] = 0.12f;
            hsb[1] = Math.max (0.3f, hsb[1]);
            int c = Color.HSBtoRGB (hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
            colorHighlight = "#" + Integer.toHexString (c);
        }
        // else rely on the fallback colors set above

        Font font = UIManager.getFont ("Tree.font");
        if (font.canDisplayUpTo ("🡰") < 0) leftArrow = "🡰";
        else                                leftArrow = "<--";

        backgroundFocused         = null;
        backgroundSelected        = null;
        backgroundFocusedSelected = null;

        o = UIManager.get ("Tree:TreeCell[Enabled+Focused].backgroundPainter");
        if (o instanceof Painter<?>) backgroundFocused = (Painter<JComponent>) o;

        o = UIManager.get ("Tree:TreeCell[Enabled+Selected].backgroundPainter");
        if (o instanceof Painter<?>) backgroundSelected = (Painter<JComponent>) o;

        o = UIManager.get ("Tree:TreeCell[Focused+Selected].backgroundPainter");
        if (o instanceof Painter<?>) backgroundFocusedSelected = (Painter<JComponent>) o;
    }

    public void updateUI ()
    {
        super.updateUI ();

        Insets margins = UIManager.getInsets ("Tree.rendererMargins");
        if (margins != null) setBorder (new EmptyBorder (margins));
    }

    /**
        @param row Used to determine if this cell is the DnD drop target. Usually, this should be >= 0.
        However, if this is being used to render cells that don't properly exist in the given tree, pass -2.
        Do not pass -1, as it will produce unexpected results in the logic for identifying DnD cells.
    **/
    public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        this.selected = selected;
        this.hasFocus = hasFocus;

        JTree.DropLocation dropLocation = tree.getDropLocation ();
        isDropCell =  dropLocation != null  &&  dropLocation.getChildIndex () == -1  &&  tree.getRowForPath (dropLocation.getPath ()) == row;

        applyComponentOrientation (tree.getComponentOrientation ());

        // Sometimes we just want a basic set up of the component, even though there is nothing to show.
        // This guard leaves the label with lingering icon and text, but the caller will override them.
        if (value == null) return this;

        NodeBase n = (NodeBase) value;

        Color fg        = getForegroundFor (n, selected  ||  isDropCell);  // Currently, we don't use colorDropCell, but rather the usual foreground that indicates override state.
        Font  fontBase  = getBaseFont (tree);
        Font  fontPlain = n.getPlainFont (fontBase);

        boolean zoomed =  fontBase == PanelModel.instance.panelEquations.panelEquationGraph.graphPanel.scaledTreeFont;
        iconHolder.setIcon (getIconFor (n, expanded, leaf, zoomed));  // If icon is null, this should result in a zero-sized label.

        List<Integer> columnWidths = null;
        FontMetrics fm = getFontMetrics (n.getStyledFont (fontBase));
        NodeBase p = n.getTrueParent ();
        if (p != null) columnWidths = p.getMaxColumnWidths (n.getColumnGroup (), fm);
        int widthCount = 0;
        if (columnWidths != null) widthCount = columnWidths.size ();

        List<String> columns = n.getColumns (selected, expanded);
        int last = columns.size () - 1;
        while (labels.size () <= last)
        {
            JLabel l = new JLabel ();
            add (l);
            labels.add (l);
        }
        int sum = getTextOffset ();
        int i = 0;
        for (; i <= last; i++)
        {
            String text = columns.get (i);
            JLabel l = labels.get (i);
            l.setForeground (fg);
            l.setFont (fontPlain);
            l.setVisible (true);
            l.setText (text);

            l.setPreferredSize (null);  // Necessary so getPreferredSize() computes a fresh value.
            if (i < last)  // Set column width.
            {
                Dimension d = l.getPreferredSize ();
                if (i < widthCount)
                {
                    d.width = columnWidths.get (i);
                    l.setPreferredSize (d);
                }
                sum += d.width;
            }
            else if (n.allowTruncate ())  // Ensure last column is not too wide.
            {
                boolean truncate = false;
                String[] pieces = text.split ("\n", 2);
                if (pieces.length > 1)
                {
                    truncate = true;
                    text = pieces[0];
                    l.setText (text);
                }

                int width = availableWidth (n.getTree ()) - sum;
                Dimension d = l.getPreferredSize ();
                if (d.width > width)
                {
                    truncate = true;
                    width = Math.max (0, width - fm.getMaxAdvance () * 2);  // allow 2em for ellipsis
                    int characters = (int) Math.floor ((double) text.length () * width / d.width);  // A crude estimate. Just take a ratio of the number of characters, rather then measuring them exactly.
                    text = text.substring (0, characters);
                }
                if (truncate)
                {
                    text += " ...";
                    l.setText (text);
                    n.wasTruncated ();
                }
            }
        }
        last = labels.size () - 1;
        for (; i <= last; i++) labels.get (i).setVisible (false);
        invalidate ();  // Force layout to update with new column sizes.

        return this;
    }

    public Font getBaseFont (JTree tree)
    {
        return tree.getFont ();
    }

    public Icon getIconFor (NodeBase node, boolean expanded, boolean leaf, boolean zoomed)
    {
        if (hideIcon) return null;

        Icon result = null;
        if (bigIcon)  // OK to use icon larger than 16x16. Implies that node is a NodePart.
        {
            NodePart np = (NodePart) node;
            result = np.iconCustom;
        }
        if (result == null) result = node.getIcon (expanded);  // A node knows whether it should hold other nodes or not, so no need to pass leaf to it.
        if (result == null)
        {
            if      (leaf)     result = iconLeaf;
            else if (expanded) result = iconOpen;
            else               result = iconClosed;
        }

        // At this point, we definitely have a non-null result.
        if (! zoomed) return result;
        double zoom = PanelModel.instance.panelEquations.panelEquationGraph.graphPanel.zoom;
        if (Math.abs (zoom - 1) < 0.05) return result;
        int w = (int) Math.round (result.getIconWidth  () * zoom);
        int h = (int) Math.round (result.getIconHeight () * zoom);
        if (w == 0  ||  h == 0) return null;
        // TODO: Cache scaled versions of icons. Should use something like MultiResolutionImage.
        Image i = ((ImageIcon) result).getImage ().getScaledInstance (w, h, Image.SCALE_SMOOTH);
        return new ImageIcon (i);
    }

    public static Color getForegroundFor (NodeBase node, boolean selected)
    {
        switch (node.getForegroundColor ())
        {
            case NodeBase.OVERRIDE: return selected ? colorSelectedOverride : colorOverride;
            case NodeBase.KILL:     return selected ? colorSelectedKill     : colorKill;
            default:    /*INHERIT*/ return selected ? colorSelectedInherit  : colorInherit;
        }
    }

    public int getIconWidth ()
    {
        Icon icon = iconHolder.getIcon ();
        if (icon == null) return 0;
        return icon.getIconWidth ();
    }

    public int getIconTextGap ()
    {
        return iconHolder.getIconTextGap ();
    }

    /**
        Does the same job as DefaultCellTreeRenderer.getLabelStart(), except that the
        number returned by this function is the actual start pixel, rather than 1 less.
    **/
    public int getTextOffset ()
    {
        Icon icon = iconHolder.getIcon ();
        if (icon == null) return 0;
        return icon.getIconWidth () + iconHolder.getIconTextGap ();
    }

    public Dimension getPreferredSize ()
    {
        // Hack to force new cells to full height, even though they contain no text.
        boolean emptyText = label.getText ().isEmpty ();
        boolean emptyIcon = iconHolder.getIcon () == null;
        if (emptyText) label.setText ("M");
        if (emptyIcon) iconHolder.setIcon (iconLeaf);
        Dimension result = super.getPreferredSize ();
        if (emptyText) label.setText ("");
        if (emptyIcon) iconHolder.setIcon (null);

        result.width += 3;  // per DefaultTreeCellEditor
        return result;
    }

    /**
        Utility function for deciding when to truncate text field.
        @return Usable width of tree's viewport. This is calculated based on possible viewport
        rather than actual viewport, because the viewport may get resized to fit the resulting field.
    **/
    public static int availableWidth (PanelEquationTree pet)
    {
        int width = 800;
        PanelEquations pe = PanelModel.instance.panelEquations;
        if (pe.view == PanelEquations.NODE)
        {
            JViewport vp = pe.panelEquationGraph.getViewport ();
            if (pet.root == pe.part)  // parent node
            {
                Insets insets = pe.panelParent.getInsets ();
                width = vp.getExtentSize ().width / 2 - insets.left - insets.right;
            }
            else if (pet.root.graph != null)  // child node
            {
                Insets insets = pet.root.graph.getInsets ();
                width = vp.getExtentSize ().width - insets.left - insets.right;
            }
        }
        else  // property panel
        {
            // Neither the tree nor the viewport should have insets.
            width = pet.getViewport ().getWidth ();
        }
        return Math.max (100, width);  // never less than 100px
    }

    /**
        Paint selection background and focus border.
        The actual contents of the cell are painted by JLabel when it is called by our superclass (JPanel).
    **/
    public void paint (Graphics g)
    {
        if (nontree  &&  backgroundFocusedSelected != null)
        {
            Graphics2D g2 = (Graphics2D) g;  // Don't create/dispose Graphics, because the painters shouldn't modify g much.
            if (hasFocus)
            {
                if (selected) backgroundFocusedSelected.paint (g2, this, getWidth (), getHeight ());
                else          backgroundFocused        .paint (g2, this, getWidth (), getHeight ());
            }
            else if (selected)  // and not focused
            {
                backgroundSelected.paint (g2, this, getWidth (), getHeight ());
            }
        }

        Color bColor;
        if (isDropCell)
        {
            bColor = colorBackgroundDropCell;
            if (bColor == null) bColor = colorBackgroundSelected;
        }
        else if (selected)
        {
            bColor = colorBackgroundSelected;
        }
        else
        {
            bColor = colorBackground;
            if (bColor == null) bColor = label.getBackground ();
        }

        int imageOffset = getTextOffset () - 1;
        boolean isLeftToRight = getComponentOrientation ().isLeftToRight ();
        int h = getHeight ();

        if (bColor != null  &&  fillBackground)
        {
            int x = isLeftToRight ? imageOffset : 0;
            int w = getWidth () - imageOffset;
            g.setColor (bColor);
            g.fillRect (x, 0, w, h);
        }

        if (hasFocus)
        {
            if (drawsFocusBorderAroundIcon) imageOffset = 0;
            int x = isLeftToRight ? imageOffset : 0;
            int w = getWidth () - imageOffset;

            if (colorBorderSelected != null  &&  (selected  ||  ! drawDashedFocusIndicator))
            {
                g.setColor (colorBorderSelected);
                g.drawRect (x, 0, w - 1, h - 1);
            }
            if (drawDashedFocusIndicator  &&  bColor != null)
            {
                g.setColor (new Color (~bColor.getRGB ()));
                BasicGraphicsUtils.drawDashedRect (g, x, 0, w, h);
            }
        }

        super.paint (g);
    }

    public class ColumnLayout implements LayoutManager2
    {
        public void invalidateLayout (Container target)
        {
        }

        public void addLayoutComponent (String name, Component comp)
        {
        }

        public void removeLayoutComponent (Component comp)
        {
        }

        public void addLayoutComponent (Component comp, Object constraints)
        {
        }

        public Dimension preferredLayoutSize (Container target)
        {
            Dimension result = new Dimension ();
            for (Component c : target.getComponents ())
            {
                if (! c.isVisible ()) continue;
                Dimension d = c.getPreferredSize ();
                result.width += d.width;
                result.height = Math.max (result.height, d.height);
            }

            Insets insets = target.getInsets ();
            result.width  += insets.left + insets.right;
            result.height += insets.top + insets.bottom;
            return result;
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
            return 0.5f;
        }

        public void layoutContainer (Container target)
        {
            int h = target.getHeight ();
            int x = target.getInsets ().left;
            for (Component c : target.getComponents ())
            {
                Dimension d = c.getPreferredSize ();
                int y = Math.max (0, h - d.height) / 2;
                c.setBounds (x, y, d.width, d.height);
                x += d.width;
            }
        }
    }
}
