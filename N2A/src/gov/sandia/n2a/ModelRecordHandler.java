/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a;

import java.awt.Component;

import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ModelRecordHandler implements RecordHandler
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
    public Component getComponent (UIController uiController)
    {
        return new ModelEditPanel (uiController);
    }
}
