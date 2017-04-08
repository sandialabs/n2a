/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.awt.Component;

import javax.swing.ImageIcon;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface Settings extends ExtensionPoint
{
    public String    getName ();
    public ImageIcon getIcon ();

    /**
        Returns a panel to go in the settings tab. Can actually be any AWT component.
    **/
    public Component getPanel ();

    /**
        Returns the component within the panel which should receive focus when the
        panel is exposed for the first time. After that, the settings tab code will
        keep track of the last-focused component and select it when the panel is
        re-exposed. Can return null.
    **/
    public Component getInitialFocus (Component panel);
}
