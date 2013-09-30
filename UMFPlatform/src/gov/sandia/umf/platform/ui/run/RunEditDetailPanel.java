/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.run;

import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.ui.UIController;

public abstract class RunEditDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    protected Run run;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public RunEditDetailPanel(UIController uic, Run r) {
        super(uic, null);
        run = r;
    }

    public void setRun(Run r) {
        run = r;
        reload();
    }
}
