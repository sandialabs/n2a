/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.awt.Component;

import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ActivityReference implements Activity
{
    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("book.gif");
    }

    @Override
    public String getName ()
    {
        return "References";
    }

    @Override
    public Component getPanel ()
    {
        return new PanelReference ();
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return ((PanelReference) panel).panelSearch.textQuery;
    }
}
