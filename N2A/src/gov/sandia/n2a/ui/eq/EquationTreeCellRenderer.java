/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.Painter;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.tree.TreeCellRenderer;

import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

/**
    Extends the standard tree cell renderer to get icon and text style from NodeBase.
    This is the core code that makes NodeBase work as a tree node representation.
**/
@SuppressWarnings("serial")
public class EquationTreeCellRenderer extends JPanel implements TreeCellRenderer
{
    protected JLabel  label = new JLabel ();
    protected boolean selected;
    protected boolean hasFocus;
    protected boolean isDropCell;
    protected boolean nontree;  // Need hack to paint background

    // These colors may get changed when look & feel is changed.
    public static Color colorInherit          = Color.blue;
    public static Color colorOverride         = Color.black;
    public static Color colorKill             = Color.red;
    public static Color colorSelectedInherit  = Color.blue;
    public static Color colorSelectedOverride = Color.black;
    public static Color colorSelectedKill     = Color.red;

    public static final int gapAfterIcon = 4;  // equivalent to DefaultTreeCellRenderer.iconTextGap

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
        Lay.BLtg (this, "C", label);
        setOpaque (false);
    }

    @SuppressWarnings("unchecked")
    public static void staticUpdateUI ()
    {
        // Check colors to see if text is dark or light.
        Color fg = UIManager.getColor ("Tree.textForeground");
        float[] hsb = Color.RGBtoHSB (fg.getRed (), fg.getGreen (), fg.getBlue (), null);
        if (hsb[2] > 0.5)  // Light text
        {
            colorInherit  = new Color (0xC0C0FF);  // light blue
            colorOverride = Color.white;
            colorKill     = Color.pink;
        }
        else  // Dark text
        {
            colorInherit  = Color.blue;
            colorOverride = Color.black;
            colorKill     = Color.red;
        }

        fg = UIManager.getColor ("Tree.selectionForeground");
        Color.RGBtoHSB (fg.getRed (), fg.getGreen (), fg.getBlue (), hsb);
        if (hsb[2] > 0.5)  // Light text
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

        label.setIcon (getIconFor (n, expanded, leaf));
        label.setForeground (getForegroundFor (n, selected  ||  isDropCell));  // Currently, we don't use colorDropCell, but rather the usual foreground that indicates override state.

        Font baseFont = tree.getFont ();
        label.setFont (n.getPlainFont (baseFont));
        if (n.needsInitTabs ()) n.initTabs (getFontMetrics (n.getStyledFont (baseFont)));
        label.setText (n.getText (expanded, false));

        return this;
    }

    public Icon getIconFor (NodeBase node, boolean expanded, boolean leaf)
    {
        Icon result = node.getIcon (expanded);  // A node knows whether it should hold other nodes or not, so don't pass leaf to it.
        if (result != null) return result;
        if (leaf)     return iconLeaf;
        if (expanded) return iconOpen;
        return               iconClosed;
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
        Icon icon = label.getIcon ();
        if (icon == null) return 0;
        return icon.getIconWidth ();
    }

    /**
        Does the same job as DefaultCellTreeRenderer.getLabelStart(), except that the
        number returned by this function is the actual start pixel, rather than 1 less.
    **/
    public int getTextOffset ()
    {
        Icon icon = label.getIcon ();
        if (icon == null) return 0;
        return icon.getIconWidth () + gapAfterIcon;
    }

    public Dimension getPreferredSize ()
    {
        // Hack to force new cells to full height, even though they contain no text.
        boolean empty = label.getText ().isEmpty ();
        if (empty) label.setText ("M");
        Dimension result = super.getPreferredSize ();
        if (empty) label.setText ("");

        result.width += 3;  // per DefaultTreeCellEditor
        return result;
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
}
