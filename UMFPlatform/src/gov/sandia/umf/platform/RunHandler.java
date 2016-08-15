/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import java.awt.Component;

import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.jobs.RunPanel;

import javax.swing.ImageIcon;

public class RunHandler implements RecordHandler
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
    public Component getComponent (UIController uiController)
    {
        return new RunPanel ();
    }
}
