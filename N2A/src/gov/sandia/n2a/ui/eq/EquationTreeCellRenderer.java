/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.Icon;
import javax.swing.JTree;
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
    // These colors may get changed when look & feel is changed.
    public static Color colorInherit          = Color.blue;
    public static Color colorOverride         = Color.black;
    public static Color colorKill             = Color.red;
    public static Color colorSelectedInherit  = Color.blue;
    public static Color colorSelectedOverride = Color.black;
    public static Color colorSelectedKill     = Color.red;

    public static void earlyUpdateUI ()
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

    public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

        NodeBase n = (NodeBase) value;

        Color fg;
        int color = n.getForegroundColor ();
        switch (color)
        {
            case NodeBase.OVERRIDE:
                fg = selected ? colorSelectedOverride : colorOverride;
                break;
            case NodeBase.KILL:
                fg = selected ? colorSelectedKill : colorKill;
                break;
            default:  // INHERIT
                fg = selected ? colorSelectedInherit : colorInherit;
        }
        setForeground (fg);

        setIcon (getIconFor (n, expanded, leaf));
        Font f = getFontFor (tree, n);
        setFont (f);
        if (n.needsInitTabs ())
        {
            n.initTabs (getFontMetrics (f));
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

    public Font getFontFor (JTree tree, NodeBase node)
    {
        Font  baseFont     = tree.getFont ().deriveFont (Font.PLAIN);
        float baseFontSize = baseFont.getSize2D ();

        int   style = node.getFontStyle ();
        float scale = node.getFontScale ();
        if (style != Font.PLAIN  ||  scale != 1) return baseFont.deriveFont (style, baseFontSize * scale);
        return baseFont;
    }
}
