/*
Copyright 2013,2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Component;

import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ActivityModel implements Activity
{
    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("comp.gif");
    }

    @Override
    public String getName ()
    {
        return "Models";
    }

    @Override
    public Component getPanel ()
    {
        return new PanelModel ();
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return ((PanelModel) panel).panelSearch.textQuery;
    }
}
