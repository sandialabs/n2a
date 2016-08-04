/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;

public class NodeAnnotation extends NodeBase
{
    public static ImageIcon icon = ImageUtil.getImage ("edit.gif");

    public String name;
    public String value;

    public NodeAnnotation (String name, String value)
    {
        this.name  = name;
        this.value = value;

        if (value.isEmpty ()) setUserObject (name);
        else                  setUserObject (name + " = " + value);
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
    }

    @Override
    public void add (String type, EquationTreePanel model)
    {
        NodeBase parent = (NodeBase) getParent ();
        if (type.isEmpty ()) parent.add ("Annotation", model);  // By context, we assume the user wants to add another annotation.
        else                 parent.add (type, model);
    }

    @Override
    public void applyEdit ()
    {
        String input = (String) getUserObject ();

        String[] parts = input.split ("=", 2);
        name = parts[0];
        if (parts.length > 1) value = parts[1];
        else                  value = "";
    }
}
