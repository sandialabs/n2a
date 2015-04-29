/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.TabbedRecordEditPanel;
import gov.sandia.umf.platform.ui.UIController;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.windows.Dialogs;
import replete.util.Lay;

public class ModelEditPanel extends TabbedRecordEditPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private ModelOrient model;

    // UI

    private GeneralDetailPanel pnlGeneralDetail;
    private TopologyDetailPanel pnlTopoDetail;
    private OutputEquationDetailPanel pnlOutputEqDetail;
    private NotesTagsDetailPanel pnlNotesTagsDetailPanel;
    private RunDetailPanel pnlRunDetailPanel;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ModelEditPanel(UIController uic, NDoc m) {
        super(uic, m);
        model = new ModelOrient(m);

        addTab("General", createGeneralPanel());
        addTab("Topology", createTopologyPanel());
        addTab("Outputs", createOutputsPanel());
        addTab("Notes/Tags", createNotesPanel());
        addTab("Runs", createRunsPanel());

        Lay.BLtg(this,
            "N", createRecordControlsPanel(),
            "C", Lay.p(tabSections, "bg=180")
        );

        reload();

        enableDirtyNotifications();

        if(!model.isPersisted()) {
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
        if(model.getName() == null || model.getName().equals("")) {
            tabSections.setSelectedIndex(0);
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
        pnlGeneralDetail = new GeneralDetailPanel(uiController, model);
        pnlGeneralDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlGeneralDetail;
    }

    private TopologyDetailPanel createTopologyPanel() {
        pnlTopoDetail = new TopologyDetailPanel(uiController, model);
        pnlTopoDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlTopoDetail;
    }

    private OutputEquationDetailPanel createOutputsPanel() {
        pnlOutputEqDetail = new OutputEquationDetailPanel(uiController, model);
        pnlOutputEqDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlOutputEqDetail;
    }

    private NotesTagsDetailPanel createNotesPanel() {
        pnlNotesTagsDetailPanel = new NotesTagsDetailPanel(uiController, model);
        pnlNotesTagsDetailPanel.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlNotesTagsDetailPanel;
    }

    private RunDetailPanel createRunsPanel() {
        pnlRunDetailPanel = new RunDetailPanel(uiController, model, this);
        pnlRunDetailPanel.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlRunDetailPanel;
    }


    //////////
    // MISC //
    //////////

    @Override
    public void doInitialFocus() {
        pnlGeneralDetail.focusNameField();
    }
}
