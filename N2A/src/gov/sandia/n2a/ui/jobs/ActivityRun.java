/*
Copyright 2013,2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Component;

import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ActivityRun implements Activity
{
    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("run.gif");
    }

    @Override
    public String getName ()
    {
        return "Runs";
    }

    @Override
    public Component getPanel ()
    {
        return new PanelRun ();
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return ((PanelRun) panel).tree;
    }
}
