/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.eq.tree;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodePart extends NodeBase
{
    protected static ImageIcon icon  = ImageUtil.getImage ("comp.gif");
    protected static ImageIcon icon2 = ImageUtil.getImage ("conn.gif");

    public EquationSet part;

    public NodePart (EquationSet part)
    {
        this.part  = part;
    }

    public String getSourcePartName ()
    {
        return part.source.get ("name", part.name);
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (part.connectionBindings == null) return icon;   // compartment
        else                                 return icon2;  // connection
    }

    @Override
    public Color getForegroundColor ()
    {
        return Color.black;
    }

    @Override
    public String toString ()
    {
        return part.name + " = $include(\"" + getSourcePartName () + "\")";
    }
}
