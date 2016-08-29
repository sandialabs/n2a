/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import java.awt.Component;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;

public interface RecordHandler extends ExtensionPoint
{
    public String    getName ();
    public ImageIcon getIcon ();

    /**
        Returns a panel to go in the tabbed pane. Can actually be any AWT component.
    **/
    public Component getPanel ();

    /**
        Returns the component within the panel which should receive focus when the
        panel is exposed for the first time. After that, the tabbed pane code will
        keep track of the last-focused component and select it when the panel is
        re-exposed. Can return null.
    **/
    public Component getInitialFocus (Component panel); 
}
