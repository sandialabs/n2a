/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.part;

import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.windows.Dialogs;
import replete.util.GUIUtil;
import replete.util.Lay;


// The equation detail panel displays the equations for a given part
// and allows the complete editing of that equations set.  This is
// delegated to the equation tree panel.  This panel also allows
// an outside component to ask that an equation be opened for editing
// on this panel.

public class EquationDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private EquationTreePanel pnlEquations;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public EquationDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        Lay.BLtg(this,
            "N", Lay.hn(createLabelPanel("Local Equations", "part-local-eq"), "pref=[10,25]"),
            "C", pnlEquations = new EquationTreePanel(uic),
            "eb=10"
        );

        pnlEquations.addEqChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                List<NDoc> eqs = pnlEquations.getEquations();
                //for(NDoc eq : eqs) {
                //    eq.setOwner(record);
                //}// attempt to get the equations to cancel... not working
                record.set("eqs", eqs);
                fireContentChangedNotifier();
            }
        });
    }

    @Override
    public void reload() {
        List<NDoc> eqs = (List) record.getValid("eqs", new ArrayList<NDoc>(), List.class);
        pnlEquations.setEquations(eqs);
    }

    public void openForEditing(NDoc eq, String prefix) {
        pnlEquations.openForEditing(eq, prefix);
    }

    @Override
    public void postLayout() {
        GUIUtil.safe(new Runnable() {
            public void run() {
                pnlEquations.postLayout();
            }
        });
        final Timer t = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GUIUtil.safe(new Runnable() {
                    public void run() {
                        pnlEquations.postLayout();
                    }
                });
            }
        });
        t.setRepeats(false);
        t.start();
    }
}
