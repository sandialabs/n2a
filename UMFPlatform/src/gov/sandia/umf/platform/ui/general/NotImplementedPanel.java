/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.general;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.JPanel;

import replete.util.Lay;

public class NotImplementedPanel extends JPanel {
    public NotImplementedPanel() {
        Lay.FLtg(this,
            "L", Lay.lb("This tab is not yet implemented!", ImageUtil.getImage("excl.gif")),
            "eb=10"
        );
    }
}
