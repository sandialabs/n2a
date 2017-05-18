/*
Copyright 2013,2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Component;
import java.awt.Font;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import gov.sandia.n2a.ui.eq.tree.NodeBase;

/**
    Extends the standard tree cell renderer to get icon and text style from NodeBase.
    This is the core code that makes NodeBase work as a tree node representation.
**/
public class EquationTreeCellRenderer extends DefaultTreeCellRenderer
{
    protected Font  baseFont;
    protected float baseFontSize;

    @Override
    public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

        if (baseFont == null)
        {
            baseFont = getFont ().deriveFont (Font.PLAIN);
            baseFontSize = baseFont.getSize2D ();
        }

        NodeBase n = (NodeBase) value;
        setForeground (n.getForegroundColor ());
        setIcon (getIconFor (n, expanded, leaf));
        Font f = getFontFor (n);
        setFont (f);
        if (n.needsInitTabs ())
        {
            n.initTabs (tree.getGraphics ().getFontMetrics (f));
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

    public Font getFontFor (NodeBase node)
    {
        int   style = node.getFontStyle ();
        float scale = node.getFontScale ();
        if (style != Font.PLAIN  ||  scale != 1) return baseFont.deriveFont (style, baseFontSize * scale);
        return baseFont;
    }
}
