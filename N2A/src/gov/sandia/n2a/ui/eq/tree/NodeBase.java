/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.ui.eq.EquationTreePanel;

import java.awt.Font;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

public class NodeBase extends DefaultMutableTreeNode
{
    protected Font baseFont;

    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
    }

    public void setFont (DefaultTreeCellRenderer renderer, boolean bold, boolean italic)
    {
        if (baseFont == null) baseFont = renderer.getFont ();
        int style = Font.PLAIN;
        if (italic) style += Font.ITALIC;
        if (bold)   style += Font.BOLD;
        if (baseFont.getStyle () != style) renderer.setFont (baseFont.deriveFont (style));
    }

    public void add (String type, EquationTreePanel panel)
    {
        ((NodeBase) getParent ()).add (type, panel);  // default action is to refer the add request up the tree
    }

    public void applyEdit ()
    {
    }
}
