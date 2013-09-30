/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.records;

import gov.sandia.n2a.ui.orientdb.model.ModelEditPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.TabbedRecordEditPanel;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchResultDetails;

import javax.swing.ImageIcon;

import replete.util.Lay;
import replete.util.User;

public class ModelRecordHandler implements RecordHandler {
    @Override
    public NDoc createNewRecord() {
        /*NDoc record = new NDoc(className);
        record.set("name", "<New>");
        record.set("notes", null);
        record.set("$owner", null);*/
        return null;
    }

    @Override
    public SearchResultDetails getSearchResultListDetails(NDoc record) {
        SearchResultDetails details = new SearchResultDetails();
        details.setTitle(record.getTitle());
        details.setOwner(record.getOwner());
        details.setLastModified(record.getModified());
        details.setDescription((String) record.get("notes", null));
        details.setIcon(getIcon(record));
        return details;
    }

    @Override
    public ImageIcon getIcon(NDoc doc) {
        return ImageUtil.getImage("model.gif");
    }

    @Override
    public String getRecordTypeDisplayName(NDoc record) {
        return "Model";
    }

    @Override
    public String[] getRecordTypeSearchFields(String type) {
        return new String[] {"name"};
    }

    @Override
    public String[] getHandledRecordTypes() {
        return new String[] {"gov.sandia.umf.n2a$Model"};
    }

    public String getTitle(NDoc doc) {
        return doc.get("name");
    }

    @Override
    public RecordEditPanel getRecordEditPanel(UIController uiController, NDoc doc) {
        return new ModelEditPanel(uiController, doc);
    }

    private class ModelRecordPanel extends TabbedRecordEditPanel {


        /////////////////
        // CONSTRUCTOR //
        /////////////////

        public ModelRecordPanel(UIController uic, NDoc doc) {
            super(uic, doc);
            addTab("General", Lay.p());
            addTab("Dimensions", Lay.p());
            addTab("Topology", Lay.p());
            addTab("Inputs", Lay.p());
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

        @Override
        protected void reloadWorker() {
        }
    }

    @Override
    public boolean includeRecordInSearchResults(NDoc record) {
        boolean isTemplate = (Boolean) record.getValid("model-template-for-run-ensemble", false, Boolean.class);
        return !isTemplate && !((String) record.get("name", "$")).startsWith("$");
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

    public NDoc createNewModel() {
        NDoc record = new NDoc(getHandledRecordTypes()[0]);
        record.set("name", "<NEW>");
        record.set("$owner", User.getName());
        record.set("$modified", System.currentTimeMillis());
        return record;
    }

    @Override
    public Parameterizable unknown__getParameterizableProcessForRecord(final NDoc doc) {
        return null;
    }
}
