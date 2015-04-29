/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.ref;

import gov.sandia.n2a.ui.orientdb.part.NotesTagsDetailPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.TabbedRecordEditPanel;
import gov.sandia.umf.platform.ui.UIController;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

public class ReferenceEditPanel extends TabbedRecordEditPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private GeneralDetailPanel pnlGeneralDetail;
    private NotesTagsDetailPanel pnlNotesTagsDetailPanel;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ReferenceEditPanel(UIController uic, NDoc r) {
        super(uic, r);

        addTab("General", createGeneralPanel());
        addTab("Notes/Tags", createNotesPanel());

        Lay.BLtg(this,
            "N", createRecordControlsPanel(),
            "C", Lay.p(tabSections, "bg=180")
        );

        reload();

        enableDirtyNotifications();

        if(!record.isPersisted()) {
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
        if(((String) record.getValid("title", "", String.class)).trim().equals("")) {
            tabSections.setSelectedIndex(0);
            pnlGeneralDetail.focusNameField();
            return "Title cannot be blank.";
        }
        if(((String) record.getValid("author", "", String.class)).trim().equals("")) {
            tabSections.setSelectedIndex(0);
            pnlGeneralDetail.focusAuthorsField();
            return "Author(s) cannot be blank.";
        }
        if(((Integer) record.getValid("year", -1, Integer.class)) < 1900) {
            tabSections.setSelectedIndex(0);
            pnlGeneralDetail.focusYearField();
            return "Invalid year.";
        }
        return null;
    }


    ///////////////////////////
    // DETAIL PANEL CREATION //
    ///////////////////////////

    private GeneralDetailPanel createGeneralPanel() {
        pnlGeneralDetail = new GeneralDetailPanel(uiController, record);
        pnlGeneralDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlGeneralDetail;
    }

    private NotesTagsDetailPanel createNotesPanel() {
        pnlNotesTagsDetailPanel = new NotesTagsDetailPanel(uiController, record);
        pnlNotesTagsDetailPanel.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlNotesTagsDetailPanel;
    }


    //////////
    // MISC //
    //////////

    @Override
    public void doInitialFocus() {
        pnlGeneralDetail.focusNameField();
    }
}
