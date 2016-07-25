/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a.ui.ref;

import gov.sandia.umf.platform.connect.orientdb.ui.TabbedRecordEditPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;

import replete.util.Lay;

public class ReferenceEditPanel extends TabbedRecordEditPanel
{
    private GeneralDetailPanel pnlGeneralDetail;

    public ReferenceEditPanel (UIController uic, MNode r)
    {
        super (uic, r);

        addTab ("General", pnlGeneralDetail = new GeneralDetailPanel (uiController, record));

        Lay.BLtg (this,
            "N", createRecordControlsPanel (),
            "C", Lay.p (tabSections, "bg=180")
        );

        reload ();
    }

    @Override
    public void doInitialFocus ()
    {
        pnlGeneralDetail.focusNameField ();
    }
}
