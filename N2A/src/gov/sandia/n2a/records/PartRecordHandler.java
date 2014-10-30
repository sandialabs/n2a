/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.records;

import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.orientdb.part.PartEditPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.search.SearchResultDetails;

import java.util.Arrays;

import javax.swing.ImageIcon;

import replete.util.User;

public class PartRecordHandler implements RecordHandler {

    @Override
    public NDoc createNewRecord() {
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
        return ImageUtil.getImage("comp.gif");
    }

    @Override
    public String getRecordTypeDisplayName(NDoc record) {
        return "Part";
    }

    @Override
    public String[] getRecordTypeSearchFields(String type) {
        return new String[] {"name"};
    }

    @Override
    public String[] getHandledRecordTypes() {
        return new String[] {"gov.sandia.umf.n2a$Part"};
    }

    public String getTitle(NDoc record) {
        return record.get("name");
    }

    @Override
    public RecordEditPanel getRecordEditPanel(UIController uiController, NDoc doc) {
        return new PartEditPanel(uiController, doc);
    }
    @Override
    public boolean includeRecordInSearchResults(NDoc record) {
        return !((String) record.get("name", "$")).startsWith("$");
    }

    @Override
    public boolean providesToString(NDoc doc) {
        return true;
    }

    @Override
    public String getToString(NDoc doc) {
        return (String) doc.get("name") + " [DIRTY=" + doc.isDirty() + ", FIELDS=" + Arrays.toString(doc.source.getDirtyFields()) + "]";
    }
    @Override
    public boolean includeTypeInSearchResults(String type) {
        return true;
    }
    public NDoc createNewPart(String type) {
        NDoc record = new NDoc(getHandledRecordTypes()[0]);
        record.set("name", "<NEW>");
        record.set("type", type);
        record.set("$owner", User.getName());
        record.set("$modified", System.currentTimeMillis());
        System.out.println(record.getId());
        return record;
    }
    @Override
    public Parameterizable unknown__getParameterizableProcessForRecord(NDoc doc) {
        return null;
    }
}
