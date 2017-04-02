/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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
