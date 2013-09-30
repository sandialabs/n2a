/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.profile;


public class GeneralDetailPanel {
/*

    ////////////
    // FIELDS //
    ////////////

    // UI

    private DualTextFieldPanel pnlOwner;
    private DualTextFieldPanel pnlName;
    private DualTextFieldPanel pnlOrg;
    private DualTextFieldPanel pnlEmail;
    private boolean noFire;


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

    public GeneralDetailPanel(UIController uic, Profile p) {
        super(uic, p);

        boolean readOnly = !User.getName().equals(profile.getUserName());

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("User ID", "owner"), "alignx=0,pref=[10,25]"),
                Lay.BL(
                    "W", Lay.BL(
                        "C", pnlOwner = new DualTextFieldPanel(true)
                    ),
                    "C", Lay.p(),
                    "alignx=0"
                ),
                Lay.hn(createLabelPanel("Name", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(pnlName = new DualTextFieldPanel(readOnly), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Organization", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(pnlOrg = new DualTextFieldPanel(readOnly), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("E-mail", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(pnlEmail = new DualTextFieldPanel(readOnly), "alignx=0,pref=[10,30]")
            ),
            "C", Lay.p(),
            "eb=10"
        );

        pnlName.addTextChangeListener(docListener);
        pnlOrg.addTextChangeListener(docListener);
        pnlEmail.addTextChangeListener(docListener);
    }

    ChangeListener docListener = new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
            if(!noFire) {
                profile.setName(pnlName.getText());
                profile.setOrganization(pnlOrg.getText());
                profile.setEmail(pnlEmail.getText());
                fireContentChangedNotifier();
            }
        }
    };

    @Override
    public void reload() {
        pnlOwner.setText(profile.getUserName());
        noFire = true;
        pnlName.setText(profile.getName());
        pnlOrg.setText(profile.getOrganization());
        pnlEmail.setText(profile.getEmail());
        noFire = false;
    }

    public void focusNameField() {
        pnlName.focus();
    }
    public void focusOrgField() {
        pnlOrg.focus();
    }
    public void focusEmailField() {
        pnlEmail.focus();
    }*/
}
