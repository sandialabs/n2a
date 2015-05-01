/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeEquation extends NodeBase {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static ImageIcon icon = ImageUtil.getImage("expr.gif");

    // Core

    protected NDoc eq;        // DB-Related Object
    protected boolean overriding;
    protected boolean overridden;
    protected EquationEntry peq;  // Sync'ed with current DB record.
    // Right now there's no way to enforce that the 'value' of the 'eq'
    // object hasn't changed since the last update.  But code should
    // use the 'setEqValue' method as opposed to something like
    // 'getEq().setValue(v)'.


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NodeEquation (NDoc e)
    {
        eq = e;
        updateParsedEquation ();
    }

    public NodeEquation (EquationEntry e)
    {
        peq = e;
        eq = e.source;
    }

    private void updateParsedEquation ()
    {
        try
        {
            peq = new EquationEntry (eq);
        }
        catch (Exception e)
        {
            peq = null;
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public NDoc getEq() {
        return eq;
    }
    public void setEqValue(String newValue) {
        eq.set("value", newValue);
        updateParsedEquation();
    }
    public EquationEntry getParsed ()
    {
        return peq;
    }
    public void setOverriding(boolean ov) {
        overriding = ov;
    }
    public void setOverridden(boolean ov) {
        overridden = ov;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public boolean isCollapsible() {
        return false;
    }
    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }
    @Override
    public Color getForegroundColor() {
        Color grn = new Color(0, 120, 0);
        return (overridden ? Color.red : (overriding ? grn : Color.black));
    }
    @Override
    public String toString ()
    {
        if (peq == null) return "<Error (eq='" + eq + "')>";
        return peq.toString ();
    }
}
