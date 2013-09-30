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
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchResultDetails;

import javax.swing.ImageIcon;

import replete.util.Lay;

public class ProfileRecordHandler implements RecordHandler {

    @Override
    public SearchResultDetails getSearchResultListDetails(NDoc record) {
        SearchResultDetails details = new SearchResultDetails();
        details.setTitle(record.getTitle());
        details.setOwner(record.getOwner());
        details.setLastModified(record.getModified());
        details.setDescription((String) record.get("org", null));
        details.setIcon(getIcon(record));
        return details;
    }

    @Override
    public ImageIcon getIcon(NDoc doc) {
        return ImageUtil.getImage("user.png");
    }

    @Override
    public String getRecordTypeDisplayName(NDoc record) {
        return "Profile";
    }

    public String[] getRecordTypeSearchFields(String type) {
        return new String[] {"name", "org"};
    }

    @Override
    public String[] getHandledRecordTypes() {
        return new String[] {"gov.sandia.umf.platform$Profile"};
    }

    public String getTitle(NDoc doc) {
        return doc.get("name");
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
