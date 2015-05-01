/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.model.topotree;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeBridgeLayer extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage("layer.gif");

    public String name;
    public String alias;

    public NodeBridgeLayer (String name, String alias)
    {
        this.name  = name;
        this.alias = alias;
    }

    @Override
    public boolean isCollapsible ()
    {
        return true;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public String toString ()
    {
        return alias + " = " + name;
    }
}
