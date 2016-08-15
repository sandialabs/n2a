/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import java.awt.Component;

import gov.sandia.umf.platform.ui.UIController;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;

public interface RecordHandler extends ExtensionPoint
{
    public String     getName ();
    public ImageIcon  getIcon ();
    public Component getComponent (UIController uiController);
}
