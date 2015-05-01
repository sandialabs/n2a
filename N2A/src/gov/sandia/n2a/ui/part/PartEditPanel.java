/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.part;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.TabbedRecordEditPanel;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.Component;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.windows.Dialogs;
import replete.util.Lay;

public class PartEditPanel extends TabbedRecordEditPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private GeneralDetailPanel pnlGeneralDetail;
    private ParentDetailPanel pnlParentDetail;
    private EquationDetailPanel pnlEquationDetail;
    private IncludeDetailPanel pnlIncludeDetail;
    private ConnectDetailPanel pnlConnectDetail;
    private NotesTagsDetailPanel pnlNotesTagsDetailPanel;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public PartEditPanel(UIController uic, NDoc p) {
        super(uic, p);

        addTab("General", createGeneralPanel());
        addTab("Parent", createParentPanel());
        addTab("Equations", createEquationPanel());
        addTab("Notes/Tags", createNotesPanel());
        addTab("Includes", createIncludePanel());
        if(!((String) record.get("type", "COMPARTMENT")).equalsIgnoreCase("COMPARTMENT")) {
            addTab("Connects", createConnectPanel());
        }

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
    protected void doCancel() {
        super.doCancel();
    }
    @Override
    protected boolean doReload() {
        if(super.doReload()) {
            return true;
        }
        return false;
    }

    @Override
    public String validationMessage() {
        if(record.get("name") == null || record.get("name").equals("")) {
            tabSections.setSelectedTab("General");
            pnlGeneralDetail.focusNameField();
            return "Name cannot be blank.";
        }
        return null;
    }

    @Override
    public String attemptToSaveSynchronous() {
        if(pnlGeneralDetail.nameFieldHasFocus()) {
            pnlGeneralDetail.nameFieldFocusLost();
        }
        String msg = validationMessage();
        if(msg != null) {
            return msg;
        }
        uiController.saveSynchronousRecursive(record);
        loadFromRecord();
        return null;
    }

    @Override
    public void attemptToSaveDialogFail() {
        String msg = validationMessage();
        if(msg != null) {
            Dialogs.showWarning(msg);
            return;
        }
        uiController.saveRecursive(record, new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                loadFromRecord();
            }
        });
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

    private ParentDetailPanel createParentPanel() {
        pnlParentDetail = new ParentDetailPanel(uiController, record);
        pnlParentDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlParentDetail;
    }

    private EquationDetailPanel createEquationPanel() {
        pnlEquationDetail = new EquationDetailPanel(uiController, record);
        pnlEquationDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlEquationDetail;
    }

    private IncludeDetailPanel createIncludePanel() {
        pnlIncludeDetail = new IncludeDetailPanel(uiController, record);
        pnlIncludeDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlIncludeDetail;
    }
    private ConnectDetailPanel createConnectPanel() {
        pnlConnectDetail = new ConnectDetailPanel(uiController, record);
        pnlConnectDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlConnectDetail;
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

    // not working yet.
    @Override
    public void postLayout() {
        for(Component c : tabSections.getAllTabComponents()) {
            if(c instanceof RecordEditDetailPanel) {
                ((RecordEditDetailPanel) c).postLayout();
            }
        }
    }
}
