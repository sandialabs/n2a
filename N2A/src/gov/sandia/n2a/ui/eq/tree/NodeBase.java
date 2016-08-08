/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.EquationTreePanel;

import java.awt.Font;
import java.util.Enumeration;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

public class NodeBase extends DefaultMutableTreeNode
{
    protected Font baseFont;
    public MPart source;

    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        setFont (renderer, false, false);
    }

    public void setFont (DefaultTreeCellRenderer renderer, boolean bold, boolean italic)
    {
        if (baseFont == null) baseFont = renderer.getFont ();
        int style = Font.PLAIN;
        if (italic) style += Font.ITALIC;
        if (bold)   style += Font.BOLD;
        if (baseFont.getStyle () != style) renderer.setFont (baseFont.deriveFont (style));
    }

    public NodeBase child (String key)
    {
        Enumeration i = children ();
        while (i.hasMoreElements ())
        {
            NodeBase n = (NodeBase) i.nextElement ();
            if (n.source.key ().equals (key)) return n;
        }
        return null;
    }

    public NodeBase add (String type, EquationTreePanel panel)
    {
        return ((NodeBase) getParent ()).add (type, panel);  // default action is to refer the add request up the tree
    }

    public boolean allowEdit ()
    {
        return true;  // Most nodes are editable. Must specifically block editing.
    }

    public void applyEdit (DefaultTreeModel model)
    {
        System.out.println ("NodeBase.applyEdit: " + this);
    }
}
