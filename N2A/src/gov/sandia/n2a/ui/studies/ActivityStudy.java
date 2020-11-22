/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.awt.Component;

import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ActivityStudy implements Activity
{
    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("study-16.png");
    }

    @Override
    public String getName ()
    {
        return "Studies";
    }

    @Override
    public Component getPanel ()
    {
        return new PanelStudy ();
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return ((PanelStudy) panel).list;
    }
}
