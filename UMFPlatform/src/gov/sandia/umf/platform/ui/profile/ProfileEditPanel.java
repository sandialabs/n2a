/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.profile;


public class ProfileEditPanel {
/*

    ////////////
    // FIELDS //
    ////////////

    // Core

    private Profile profile;

    // UI

    private GeneralDetailPanel pnlGeneralDetail;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ProfileEditPanel(UIController uic, Profile p) {
        super(uic, p);
        profile = p;

        addTab("General", createGeneralPanel());

        Lay.BLtg(this,
            "N", createRecordControlsPanel(),
            "C", Lay.p(tabSections, "bg=180")
        );

        loadFromRecord();

        enableDirtyNotifications();

        if(!profile.isPersisted()) {
            makeDirty();
        } else {
            makeClean();
        }
    }


    //////////////////////////////////
    // BEAN VALIDATE, READ, & WRITE //
    //////////////////////////////////

    @Override
    public String validationMessage() {
        if(profile.getName() == null || profile.getName().equals("")) {
            tabSections.setSelectedIndex(0);
            pnlGeneralDetail.focusNameField();
            return "Name cannot be blank.";
        }
        if(profile.getOrganization() == null || profile.getOrganization().equals("")) {
            tabSections.setSelectedIndex(0);
            pnlGeneralDetail.focusOrgField();
            return "Organization cannot be blank.";
        }
        if(profile.getEmail() == null || profile.getEmail().equals("")) {
            tabSections.setSelectedIndex(0);
            pnlGeneralDetail.focusEmailField();
            return "E-mail address cannot be blank.";
        }
        return null;
    }


    ///////////////////////////
    // DETAIL PANEL CREATION //
    ///////////////////////////

    private GeneralDetailPanel createGeneralPanel() {
        pnlGeneralDetail = new GeneralDetailPanel(uiController, profile);
        pnlGeneralDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlGeneralDetail;
    }


    //////////
    // MISC //
    //////////

    @Override
    public void doInitialFocus() {
        pnlGeneralDetail.focusNameField();
    }*/
}
