/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;

public class DoAddDoc extends Do
{
    protected String name;

    public void undo ()
    {
        super.undo ();

        MNode doc = AppData.models.child (name);
        ModelEditPanel mep = ModelEditPanel.instance;
        mep.panelEquations.recordDeleted (doc);
        mep.panelSearch.removeDoc (doc);
        ((MDoc) doc).delete ();
    }

    public void redo ()
    {
        super.redo ();

        ModelEditPanel mep = ModelEditPanel.instance;
        mep.panelEquations.createNewModel ();  // also calls panelSearch.insertDoc()
        mep.panelEquations.tree.requestFocusInWindow ();
        name = mep.panelEquations.record.key ();
    }
}
