/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeEquation extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("equation.png");

    public EquationEntry equation;

    public NodeEquation (EquationEntry equation)
    {
        this.equation = equation;
    }

    public void parseEditedString (String input)
    {
    }

    @Override
    public boolean isCollapsible ()
    {
        return false;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public Color getForegroundColor ()
    {
        // TODO: compare record references rather than using booleans
        //Color grn = new Color (0, 120, 0);
        //return (overridden ? Color.red : (overriding ? grn : Color.black));
        return Color.black;
    }

    @Override
    public String toString ()
    {
        return equation.toString ();
    }
}
