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

public class NodeReference extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("book.gif");

    public NodeReference (MPart source)
    {
        this.source = source;
        setUserObject (source.key () + "--" + source.get ());
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
    }

    @Override
    public NodeBase add (String type, EquationTreePanel model)
    {
        NodeBase parent = (NodeBase) getParent ();
        if (type.isEmpty ()) return parent.add ("Reference", model);  // By context, we assume the user wants to add another reference.
        else                 return parent.add (type, model);
    }

    @Override
    public void applyEdit (DefaultTreeModel model)
    {
        String input = (String) getUserObject ();
        String[] parts = input.split ("=", 2);
        String name = parts[0];
        String value;
        if (parts.length > 1) value = parts[1];
        else                  value = "";

        NodeBase existingReference = null;
        String oldKey = source.key ();
        NodeBase parent = (NodeBase) getParent ();
        if (! name.equals (oldKey)) existingReference = parent.child (name);

        if (name.equals (oldKey))  // Name is the same
        {
            source.set (value);
        }
        else if (existingReference != null)  // Name is already taken, so change not permitted. 
        {
            source.set (value);
            setUserObject (oldKey + "--" + value);
            model.nodeChanged (this);
        }
        else  // Name is changed
        {
            MPart p = source.getParent ();
            MPart newPart = (MPart) p.set (value, name);
            p.clear (oldKey);
            if (p.child (oldKey) == null) source = newPart;  // We were not associated with an override, so we can re-use this tree node.
            else model.insertNodeInto (new NodeReference (newPart), parent, parent.getChildCount ());  // Make a new tree node, and leave this one to present the non-overridden value.
        }
    }
}
