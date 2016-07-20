/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.part;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.DualTextFieldPanel;
import gov.sandia.umf.platform.ui.UIController;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.util.Lay;

public class GeneralDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private DualTextFieldPanel pnlName;
    private DualTextFieldPanel pnlOwner;
    private DualTextFieldPanel pnlUID;
    private boolean fireOnLostFocus;
    private boolean noFire = false;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier nameChangedFocusLostNotifier = new ChangeNotifier(this);
    public void addNameChangedFocusLostListener(ChangeListener listener) {
        nameChangedFocusLostNotifier.addListener(listener);
    }
    protected void fireNameChangedFocusLostNotifier() {
        nameChangedFocusLostNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public GeneralDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Name", "part-name"), "alignx=0,pref=[10,25]"),
                Lay.hn(pnlName = new DualTextFieldPanel(false), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Owner", "part-owner"), "alignx=0,pref=[10,25]"),
                Lay.BL(
                    "W", pnlOwner = new DualTextFieldPanel(true),
                    "C", Lay.p(),
                    "alignx=0,pref=[10,30]"
                ),
                Lay.hn(createLabelPanel("DB UID", "part-db-uid"), "alignx=0,pref=[10,25]"),
                Lay.hn(pnlUID = new DualTextFieldPanel(true), "alignx=0,pref=[10,30]")
            ),
            "C", Lay.p(),
            "eb=10"
        );

        pnlName.addTextChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(!noFire) {
                    fireOnLostFocus = true;
                    fireContentChangedNotifier();
                }
            }
        });
        pnlName.addTextLostFocusListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(fireOnLostFocus) {
                    nameFieldFocusLost();
                }
            }
        });
    }

    @Override
    public void reload() {
        noFire = true;
        pnlName.setText((String) record.get("name"));
        noFire = false;
        pnlOwner.setText(record.getOwner());
        pnlUID.setText(!record.isPersisted() ? "<NEW>" : "" + record.getId());
//        chkDevTest.setSelected(part.isDevTest());
    }

    public void focusNameField() {
        pnlName.focus();
    }

    public void nameFieldFocusLost() {
        noFire = true;
        pnlName.setText(pnlName.getText().trim());
        noFire = false;
        record.set("name", pnlName.getText());
        fireNameChangedFocusLostNotifier();
        fireOnLostFocus = false;
    }

    public boolean nameFieldHasFocus() {
        return pnlName.hasDaFocus();
    }
}
