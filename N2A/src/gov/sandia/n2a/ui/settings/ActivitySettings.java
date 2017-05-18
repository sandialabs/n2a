/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
