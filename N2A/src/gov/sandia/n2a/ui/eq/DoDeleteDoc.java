/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;

public class DoDeleteDoc extends Do
{
    protected MVolatile saved;
    protected boolean   neutralized;
    protected int       searchIndex;  ///< Position of this doc in the search panel before it was deleted.
    protected boolean   fromSearchPanel;
    protected boolean   wasShowing;

    public DoDeleteDoc (MDoc doc, boolean fromSearchPanel, boolean wasShowing)
    {
        saved = new MVolatile (doc.key (), "");
        saved.merge (doc);  // in-memory copy of the entire document
        this.fromSearchPanel = fromSearchPanel;
        this.wasShowing      = wasShowing;
    }

    @Override
    public void undo ()
    {
        super.undo ();

        MNode doc = AppData.models.set ("", saved.key ());
        doc.merge (saved);
        ModelEditPanel mep = ModelEditPanel.instance;
        mep.panelSearch.insertDoc (doc, searchIndex);
        if (wasShowing) mep.panelEquations.loadRootFromDB (doc);
        mep.panelSearch.lastSelection = searchIndex;
        if (fromSearchPanel)
        {
            mep.panelSearch.list.setSelectedIndex (searchIndex);
            if (wasShowing) mep.panelEquations.tree.clearSelection ();
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEquations.tree.requestFocusInWindow ();
        }
    }

    @Override
    public void redo ()
    {
        super.redo ();

        MNode doc = AppData.models.child (saved.key ());
        ModelEditPanel mep = ModelEditPanel.instance;
        mep.panelEquations.recordDeleted (doc);
        searchIndex = mep.panelSearch.removeDoc (doc);
        ((MDoc) doc).delete ();
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof DoAddDoc  &&  saved.key ().equals (((DoAddDoc) edit).name))
        {
            neutralized = true;
            return true;
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}
