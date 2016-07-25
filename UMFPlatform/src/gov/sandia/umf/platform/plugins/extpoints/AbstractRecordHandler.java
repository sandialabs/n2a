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

public class AbstractRecordHandler implements RecordHandler
{
    @Override
    public MNode createNewRecord ()
    {
        return null;
    }

    @Override
    public String[] getHandledRecordTypes ()
    {
        return null;
    }

    @Override
    public String getTitle (NDoc doc)
    {
        return null;
    }

    @Override
    public ImageIcon getIcon ()
    {
        return null;
    }

    @Override
    public RecordEditPanel getRecordEditPanel (UIController uiController, MNode node)
    {
        return null;
    }

    @Override
    public String getRecordTypeDisplayName (NDoc record)
    {
        return null;
    }

    @Override
    public boolean includeRecordInSearchResults (NDoc record)
    {
        return false;
    }

    @Override
    public String[] getRecordTypeSearchFields (String type)
    {
        return null;
    }

    @Override
    public boolean providesToString (NDoc doc)
    {
        return false;
    }

    @Override
    public String getToString (NDoc source)
    {
        return null;
    }

    @Override
    public boolean includeTypeInSearchResults (String type)
    {
        return false;
    }
}
