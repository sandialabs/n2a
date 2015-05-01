/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.ui.UIController;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

public class OutputEquationDetailPanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private EquationTreePanel pnlEquations;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public OutputEquationDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m);

        Lay.BLtg(this,
            "N", Lay.hn(createLabelPanel("Output Equations", "local-eq"), "pref=[10,25]"),
            "C", pnlEquations = new EquationTreePanel(uic),
            "eb=10"
        );

        pnlEquations.addEqChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                model.setOutputEqs(pnlEquations.getEquations());
                fireContentChangedNotifier();
            }
        });
    }

    @Override
    public void reload() {
        pnlEquations.setEquations(model.getOutputEqs());
    }
}
