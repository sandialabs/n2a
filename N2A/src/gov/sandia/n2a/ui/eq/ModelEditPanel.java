/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import replete.util.Lay;

// TODO: When save is clicked from the main menu (or toolbar), any currently editing field should lose focus and write its value back to the associated MNode.
public class ModelEditPanel extends RecordEditPanel
{
    public EquationTreePanel pnlEquations;

    public ModelEditPanel(UIController uic, MNode doc)
    {
        super (uic, doc);

        Lay.BLtg (this,
            "N", createRecordControlsPanel (),
            "C", pnlEquations = new EquationTreePanel (uic, doc)
        );
    }

    @Override
    public void reload ()
    {
        pnlEquations.setEquations (record);
    }
}
