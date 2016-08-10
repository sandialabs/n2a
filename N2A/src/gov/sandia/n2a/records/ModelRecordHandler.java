/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a.records;

import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MDir;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;

public class ModelRecordHandler implements RecordHandler
{
    @Override
    public MNode createNewRecord ()
    {
        MDir models = AppData.getInstance ().models;
        MNode result = null;
        int i = 0;
        while (true)
        {
            String index = "Model" + i;
            if (models.child (index) == null)
            {
                result = models.set ("", index);
                return result;
            }
        }
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("model.gif");
    }

    @Override
    public String getRecordTypeDisplayName (NDoc record)
    {
        return "Model";
    }

    @Override
    public String[] getRecordTypeSearchFields (String type)
    {
        return new String[] {"name"};
    }

    @Override
    public String[] getHandledRecordTypes ()
    {
        return new String[] {"Model"};
    }

    public String getTitle (NDoc doc)
    {
        return doc.get ("name");
    }

    @Override
    public RecordEditPanel getRecordEditPanel (UIController uiController, MNode doc)
    {
        return new ModelEditPanel (uiController, doc);
    }

    @Override
    public boolean includeRecordInSearchResults (NDoc record)
    {
        boolean isTemplate = (Boolean) record.getValid ("model-template-for-run-ensemble", false, Boolean.class);
        return !isTemplate && !((String) record.get ("name", "$")).startsWith ("$");
    }

    @Override
    public boolean providesToString (NDoc doc)
    {
        return true;
    }

    @Override
    public String getToString (NDoc doc)
    {
        return (String) doc.get ("name");
    }

    @Override
    public boolean includeTypeInSearchResults (String type)
    {
        return true;
    }
}
