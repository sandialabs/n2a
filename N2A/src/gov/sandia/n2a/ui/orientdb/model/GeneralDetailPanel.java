/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.ui.DualTextFieldPanel;
import gov.sandia.umf.platform.ui.UIController;

import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.util.Lay;
import replete.util.User;

public class GeneralDetailPanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private DualTextFieldPanel pnlName;
    private JLabel lblOwner;
    private JLabel lblUID;
    private IconButton btnOwnerDetails;
//    private JCheckBox chkDevTest;
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

    public GeneralDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m);

        boolean readOnly = !User.getName().equals(model.getOwner());

        btnOwnerDetails = createButtonOwnerDetails(model.getSource());

        /*chkDevTest = new JCheckBox("Developer Test Record?");
        chkDevTest.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //model.setDevTest(chkDevTest.isSelected());
                fireContentChangedNotifier();
            }
        });*/

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Name", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(pnlName = new DualTextFieldPanel(readOnly), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Owner", "owner"), "alignx=0,pref=[10,25]"),
                Lay.BL(
                    "W", Lay.BL(
                        "C", lblOwner = Lay.lb("", "fg=" + Lay.clr(DARK_BLUE)),
                        "E", Lay.p(btnOwnerDetails, "eb=3l")
                    ),
                    "C", Lay.p(),
                    "alignx=0"
                ),
                Lay.hn(createLabelPanel("DB UID", "db-uid"), "alignx=0,pref=[10,25]"),
                lblUID = Lay.lb("", "fg=" + Lay.clr(DARK_BLUE))
//                Lay.hn(createCheckboxPanel(chkDevTest, "db-uid"), "alignx=0,pref=[10,25]")
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
        pnlName.setText(model.getName());
        noFire = false;
        lblOwner.setText(SPC + model.getOwner());
        lblUID.setText(SPC + (!model.isPersisted() ? "<NEW>" : "" + model.getSource().getId()));
        //chkDevTest.setSelected(model.isDevTest());
    }

    public void focusNameField() {
        pnlName.focus();
    }

    public void nameFieldFocusLost() {
        noFire = true;
        pnlName.setText(pnlName.getText().trim());
        noFire = false;
        model.setName(pnlName.getText());
        fireNameChangedFocusLostNotifier();
        fireOnLostFocus = false;
    }

    public boolean nameFieldHasFocus() {
        return pnlName.hasDaFocus();
    }
}
