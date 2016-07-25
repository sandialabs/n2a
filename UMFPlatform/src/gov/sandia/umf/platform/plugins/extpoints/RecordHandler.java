/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;

public interface RecordHandler extends ExtensionPoint
{
    public MNode           createNewRecord ();
    public String[]        getHandledRecordTypes ();
    public String          getTitle(NDoc doc);
    public ImageIcon       getIcon ();
    public RecordEditPanel getRecordEditPanel(UIController uiController, MNode node);
    public String          getRecordTypeDisplayName(NDoc record);
    public boolean         includeRecordInSearchResults(NDoc record);
    public boolean         includeTypeInSearchResults(String type);
    public String[]        getRecordTypeSearchFields(String type);
    public boolean         providesToString(NDoc doc);
    public String          getToString(NDoc source);
}
