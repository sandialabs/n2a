/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.Painter;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import gov.sandia.n2a.ui.eq.tree.NodeBase;

/**
    Extends the standard tree cell renderer to get icon and text style from NodeBase.
    This is the core code that makes NodeBase work as a tree node representation.
**/
@SuppressWarnings("serial")
public class EquationTreeCellRenderer extends DefaultTreeCellRenderer
{
    protected boolean             nontree;  // Need hack to paint background
    protected Painter<JComponent> backgroundPainter;

    // These colors may get changed when look & feel is changed.
    public static Color colorInherit          = Color.blue;
    public static Color colorOverride         = Color.black;
    public static Color colorKill             = Color.red;
    public static Color colorSelectedInherit  = Color.blue;
    public static Color colorSelectedOverride = Color.black;
    public static Color colorSelectedKill     = Color.red;

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
    }

    @SuppressWarnings("unchecked")
    public void updateUI ()
    {
        super.updateUI ();
        Object o = UIManager.get ("Tree:TreeCell[Focused+Selected].backgroundPainter");
        if (o instanceof Painter<?>) backgroundPainter = (Painter<JComponent>) o;
    }

    /**
        Special case where "value" object is known to be null but we still want a basic set up of the editing component.
    **/
    public Component getTreeCellRendererComponent (JTree tree, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        return super.getTreeCellRendererComponent (tree, null, selected, expanded, leaf, row, hasFocus);
    }

    public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

        NodeBase n = (NodeBase) value;

        Color fg = getForegroundFor (n, selected);
        setForeground (fg);

        setIcon (getIconFor (n, expanded, leaf));
        Font baseFont = tree.getFont ();
        setFont (n.getPlainFont (baseFont));
        if (n.needsInitTabs ())
        {
            n.initTabs (getFontMetrics (n.getStyledFont (baseFont)));
            // convertValueToText() is called first thing in super.getTreeCellRendererComponent(),
            // but text very likely has changed, so we need to call it again here.
            setText (tree.convertValueToText (value, selected, expanded, leaf, row, hasFocus));
        }

        return this;
    }

    public Icon getIconFor (NodeBase node, boolean expanded, boolean leaf)
    {
        Icon result = node.getIcon (expanded);  // A node knows whether it should hold other nodes or not, so don't pass leaf to it.
        if (result != null) return result;
        if (leaf)     return getDefaultLeafIcon ();
        if (expanded) return getDefaultOpenIcon ();
        return               getDefaultClosedIcon ();
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
        Icon icon = getIcon ();
        if (icon == null) return 0;
        return icon.getIconWidth ();
    }

    /**
        Does the same job as DefaultCellTreeRenderer.getLabelStart(), except that the Java library
        designer, in their infinite wisdom, made that method private. Go figure.
        There is one small difference: the number returned by this function is the
        actual start pixel, rather than 1 less.
    **/
    public int getTextOffset ()
    {
        Icon icon = getIcon ();
        if (icon == null) return 0;
        return icon.getIconWidth () + getIconTextGap ();
    }

    public void paint (Graphics g)
    {
        if (nontree  &&  backgroundPainter != null  &&  hasFocus)
        {
            Graphics2D g2 = (Graphics2D) g.create ();
            backgroundPainter.paint (g2, this, getWidth (), getHeight ());
            g2.dispose ();
        }
        super.paint (g);
    }
}
