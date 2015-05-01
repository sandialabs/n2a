/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.records;

import gov.sandia.n2a.ui.ref.ReferenceEditPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchResultDetails;

import javax.swing.ImageIcon;

public class ReferenceRecordHandler implements RecordHandler {

    @Override
    public NDoc createNewRecord() {
        return null;
    }

    @Override
    public SearchResultDetails getSearchResultListDetails(NDoc record) {
        SearchResultDetails details = new SearchResultDetails();
        details.setTitle(record.getTitle() + " (" + record.get("author") + ")");
        details.setOwner(record.getOwner());
        details.setLastModified(record.getModified());
        details.setDescription((String) record.getValid("notes", null, String.class));
        details.setIcon(getIcon(record));
        return details;
    }

    @Override
    public ImageIcon getIcon(NDoc doc) {
        return ImageUtil.getImage("book.gif");
    }

    @Override
    public String getRecordTypeDisplayName(NDoc record) {
        return "Reference";
    }

    public String[] getRecordTypeSearchFields(String type) {
        return new String[] {"title", "author"};
    }

    @Override
    public String[] getHandledRecordTypes() {
        return new String[] {"gov.sandia.umf.n2a$Reference"};
    }

    public String getTitle(NDoc doc) {
        return doc.get("title");
    }

    @Override
    public RecordEditPanel getRecordEditPanel(UIController uiController, NDoc doc) {
        return new ReferenceEditPanel(uiController, doc);
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
