/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

public class NodeEquation extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("equation.png");

    public NodeEquation (MPart source)
    {
        this.source = source;
        String key = source.key ();
        if (key.equals ("@")) setUserObject (source.get ());
        else                  setUserObject (source.get () + source.key ());  // key should start with "@"
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
    }

    @Override
    public NodeBase add (String type, EquationTreePanel panel)
    {
        if (type.isEmpty ()) type = "Equation";
        return ((NodeBase) getParent ()).add (type, panel);
    }

    @Override
    public void applyEdit (DefaultTreeModel model)
    {
        String input = (String) getUserObject ();
        String[] parts = input.split ("@", 2);
        String expression = parts[0];
        String conditional;
        if (parts.length > 1) conditional = "@" + parts[1];
        else                  conditional = "@";

        NodeBase existingEquation = null;
        String oldKey = source.key ();
        NodeBase parent = (NodeBase) getParent ();
        if (! conditional.equals (oldKey)) existingEquation = parent.child (conditional);

        if (conditional.equals (oldKey))  // Condition is the same
        {
            source.set (expression);
        }
        else if (existingEquation != null)  // Condition already exists, so no change allowed
        {
            source.set (expression);
            setUserObject (expression + oldKey);
            model.nodeChanged (this);
        }
        else  // The name was changed.
        {
            MPart p = source.getParent ();
            MPart newPart = (MPart) p.set (expression, conditional);
            p.clear (oldKey);
            if (p.child (oldKey) == null) source = newPart;  // We were not associated with an override, so we can re-use this tree node.
            else model.insertNodeInto (new NodeEquation (newPart), parent, parent.getChildCount ());  // Make a new tree node, and leave this one to present the non-overridden value.
        }
    }
}
