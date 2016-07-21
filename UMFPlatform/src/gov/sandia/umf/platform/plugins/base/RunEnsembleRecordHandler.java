/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.base;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.TabbedRecordEditPanel;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.ensemble.ParameterSpecGroupsPanel;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import javax.swing.ImageIcon;

import replete.util.Lay;
import replete.xstream.XStreamWrapper;

public class RunEnsembleRecordHandler implements RecordHandler {

    @Override
    public ImageIcon getIcon(NDoc doc) {
        return ImageUtil.getImage("runens.gif");
    }

    @Override
    public String getRecordTypeDisplayName(NDoc record) {
        return "Run Ensemble";
    }

    public String[] getRecordTypeSearchFields(String type) {
        return new String[] {"label"};
    }

    @Override
    public String[] getHandledRecordTypes() {
        return new String[] {"gov.sandia.umf.platform$RunEnsemble"};
    }

    public String getTitle(NDoc doc) {
        return doc.get("label");
    }

    @Override
    public RecordEditPanel getRecordEditPanel(UIController uiController, NDoc doc) {
        return new TestRecordPanel(uiController, doc);
    }

    private class TestRecordPanel extends TabbedRecordEditPanel {


        /////////////////
        // CONSTRUCTOR //
        /////////////////

        public TestRecordPanel(UIController uic, NDoc doc) {
            super(uic, doc);
            System.out.println(doc);

            ParameterSpecGroupSet groups = getParameterSpecGroupSet(doc);

//            addTab("General", Lay.p());
            addTab("Parameterization",
                Lay.BL(
                    "W", Lay.hn(new ParameterSpecGroupsPanel(groups), "prefw=400"),
                    "C", Lay.p())
            );
            addTab("Outputs", Lay.p());
            Lay.BLtg(this,
                "N", createRecordControlsPanel(),
                "C", Lay.p(tabSections, "bg=180")
            );
        }
        @Override
        public String validationMessage() {
            return null;
        }
        @Override
        public void doInitialFocus() {
        }

        private ParameterSpecGroupSet getParameterSpecGroupSet(NDoc dEns) {
            return (ParameterSpecGroupSet) XStreamWrapper.loadTargetFromString(
                (String) dEns.get("paramSpecs"));
        }

    }

    @Override
    public NDoc createNewRecord() {
        return null;
    }
    @Override
    public boolean includeRecordInSearchResults(NDoc record) {
        return true;
    }

    @Override
    public boolean providesToString(NDoc doc) {
        return false;
    }

    @Override
    public String getToString(NDoc doc) {
        return null;
    }

    @Override
    public boolean includeTypeInSearchResults(String type) {
        return true;
    }

    @Override
    public Parameterizable unknown__getParameterizableProcessForRecord(NDoc doc) {
        return null;
    }
}
