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

import java.awt.Color;

import javax.swing.JLabel;
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
    private DimensionsDetailPanel pnlDimensionsDetail;
    private TopologyDetailPanel pnlTopoDetail;
//    private InputEquationDetailPanel pnlInputEqDetail;
    private OutputEquationDetailPanel pnlOutputEqDetail;
//    private ModelEquationDetailPanel pnlModelEqDetail;
    private NotesTagsDetailPanel pnlNotesTagsDetailPanel;
    private RunDetailPanel pnlRunDetailPanel;
    private SummaryDetailPanel pnlSummaryDetail;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ModelEditPanel(UIController uic, NDoc m) {
        super(uic, m);
        model = new ModelOrient(m);

        addTab("General", createGeneralPanel());
//        addTab("Dimensions", createDimensionsPanel());
        addTab("Topology", createTopologyPanel());
//        addTab("Inputs", createInputsPanel());
        addTab("Outputs", createOutputsPanel());
//        addTab("Equations", createEquationsPanel());
        addTab("Notes/Tags", createNotesPanel());
//        addTab("Discussion", new NotImplementedPanel());
//        addTab("Permissions", new NotImplementedPanel());
//        addTab("References", new NotImplementedPanel());
//        addTab("Change History", new NotImplementedPanel());
        addTab("Runs", createRunsPanel());
        addTab("Summary", createSummaryPanel());

        final JLabel lbl = tabSections.getHeaderPanelAt(tabSections.indexOfTab("Summary")).getLabel();
        final Color preColor = lbl.getForeground();
        pnlSummaryDetail.addSummaryUpdateStartListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                lbl.setForeground(new Color(24, 145, 28));
            }
        });
        pnlSummaryDetail.addSummaryUpdateStopListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                lbl.setForeground(preColor);
            }
        });

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

        pnlSummaryDetail.setAllowSummaryUpdates(true);
        updateSummary();
    }


    //////////////////////////////////
    // BEAN VALIDATE, READ, & WRITE //
    //////////////////////////////////

    @Override
    protected void doCancel() {
        super.doCancel();
        if(model.isPersisted()) {
            updateSummary();
        }
    }
    @Override
    protected boolean doReload() {
        if(super.doReload()) {
            updateSummary();
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
        pnlGeneralDetail.addNameChangedFocusLostListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSummary();
            }
        });
        return pnlGeneralDetail;
    }

    private DimensionsDetailPanel createDimensionsPanel() {
        pnlDimensionsDetail = new DimensionsDetailPanel(uiController, model);
        pnlDimensionsDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSummary();
                makeDirty();
            }
        });
        return pnlDimensionsDetail;
    }

    private TopologyDetailPanel createTopologyPanel() {
        pnlTopoDetail = new TopologyDetailPanel(uiController, model);
        pnlTopoDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSummary();
                makeDirty();
            }
        });
        return pnlTopoDetail;
    }

//    private InputEquationDetailPanel createInputsPanel() {
//        pnlInputEqDetail = new InputEquationDetailPanel(uiController, model);
//        pnlInputEqDetail.addContentChangedListener(new ChangeListener() {
//            public void stateChanged(ChangeEvent e) {
//                makeDirty();
//            }
//        });
//        return pnlInputEqDetail;
//    }
//
    private OutputEquationDetailPanel createOutputsPanel() {
        pnlOutputEqDetail = new OutputEquationDetailPanel(uiController, model);
        pnlOutputEqDetail.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                makeDirty();
            }
        });
        return pnlOutputEqDetail;
    }

//    private ModelEquationDetailPanel createEquationsPanel() {
//        pnlModelEqDetail = new ModelEquationDetailPanel(uiController, model);
//        pnlModelEqDetail.addContentChangedListener(new ChangeListener() {
//            public void stateChanged(ChangeEvent e) {
//                makeDirty();
//            }
//        });
//        return pnlModelEqDetail;
//    }

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

    private SummaryDetailPanel createSummaryPanel() {
        pnlSummaryDetail = new SummaryDetailPanel(uiController, model);
        return pnlSummaryDetail;
    }


    //////////
    // MISC //
    //////////

    public void updateSummary() {
        pnlSummaryDetail.reload();
    }

    @Override
    public void doInitialFocus() {
        pnlGeneralDetail.focusNameField();
    }
}
