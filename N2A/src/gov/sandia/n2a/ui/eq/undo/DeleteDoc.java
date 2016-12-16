/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.ModelEditPanel;

public class DeleteDoc extends Do
{
    protected MVolatile saved;
    protected boolean   neutralized;
    protected int       searchIndex;  ///< Position of this doc in the search panel before it was deleted.
    protected boolean   fromSearchPanel;
    protected boolean   wasShowing;

    public DeleteDoc (MDoc doc)
    {
        saved = new MVolatile (doc.key (), "");
        saved.merge (doc);  // in-memory copy of the entire document

        ModelEditPanel mep = ModelEditPanel.instance;
        fromSearchPanel = mep.panelSearch.list.getSelectedIndex () >= 0;
        wasShowing      = mep.panelEquations.record == doc;
    }

    public void undo ()
    {
        super.undo ();

        MNode doc = AppData.models.set ("", saved.key ());
        doc.merge (saved);
        ModelEditPanel mep = ModelEditPanel.instance;
        searchIndex = mep.panelSearch.insertDoc (doc, searchIndex);
        if (wasShowing) mep.panelEquations.loadRootFromDB (doc);
        mep.panelSearch.lastSelection = searchIndex;
        if (fromSearchPanel)
        {
            if (wasShowing) mep.panelEquations.tree.clearSelection ();
            mep.panelSearch.list.setSelectedIndex (searchIndex);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEquations.tree.requestFocusInWindow ();
        }
    }

    public void redo ()
    {
        super.redo ();

        MNode doc = AppData.models.child (saved.key ());
        ModelEditPanel mep = ModelEditPanel.instance;
        mep.panelEquations.recordDeleted (doc);
        searchIndex = mep.panelSearch.removeDoc (doc);
        ((MDoc) doc).delete ();
        mep.panelSearch.lastSelection = Math.min (mep.panelSearch.model.size () - 1, searchIndex);
        if (fromSearchPanel)
        {
            mep.panelSearch.list.setSelectedIndex (mep.panelSearch.lastSelection);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEquations.tree.requestFocusInWindow ();
        }
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddDoc  &&  saved.key ().equals (((AddDoc) edit).name))
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
