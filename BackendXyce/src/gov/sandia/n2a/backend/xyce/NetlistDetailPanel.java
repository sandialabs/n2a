/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.general.NotesPanel;
import gov.sandia.umf.platform.ui.run.RunEditDetailPanel;
import replete.util.Lay;

public class NetlistDetailPanel extends RunEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private NotesPanel pnlNotes;

    // Misc

    private String runState;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NetlistDetailPanel(UIController uic, Run r, String state) {
        super(uic, r);
        runState = state;

        Lay.BLtg(this,
            "N", Lay.hn(createLabelPanel("Netlist", "notes"), "pref=[10,25]"),
            "C", pnlNotes = new NotesPanel(uiController, true),
            "eb=10"
        );
    }

    @Override
    public void reload() {
        pnlNotes.setNotes(runState);
    }
}
