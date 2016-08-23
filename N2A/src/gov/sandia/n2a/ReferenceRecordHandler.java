/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a;

import java.awt.Component;

import gov.sandia.n2a.ui.ref.ReferenceEditPanel;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ReferenceRecordHandler implements RecordHandler
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
    public Component getComponent (UIController uiController)
    {
        return new ReferenceEditPanel (uiController);
    }
}