/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a.ui.settings;

import java.awt.Component;

import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ActivitySettings implements Activity
{
    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("settings.png");
    }

    @Override
    public String getName ()
    {
        return "Settings";
    }

    @Override
    public Component getPanel ()
    {
        return new PanelSettings ();
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return ((PanelSettings) panel).getComponentAt (0);
    }
}
