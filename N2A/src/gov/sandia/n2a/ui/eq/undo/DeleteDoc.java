/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;

public class DeleteDoc extends Undoable
{
    protected MVolatile saved;
    protected boolean   neutralized;
    protected String    keyAfter;  // key of the doc in the search list immediately after the one being deleted, or empty string is this is the end of the list
    protected boolean   fromSearchPanel;
    protected boolean   wasShowing;

    public DeleteDoc (MDoc doc)
    {
        saved = new MVolatile (doc.key (), "");
        saved.merge (doc);  // in-memory copy of the entire document

        PanelModel mep = PanelModel.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();
        keyAfter        = mep.panelSearch.keyAfter (doc);
        wasShowing      = mep.panelEquations.record == doc;
    }

    public void undo ()
    {
        super.undo ();
        PanelModel mep = PanelModel.instance;
        mep.panelMRU.dontInsert = true;
        int index = mep.panelSearch.indexOf (keyAfter);
        if (index < 0) index = 0;
        AddDoc.create (saved.key (), saved, index, fromSearchPanel, wasShowing, false);
    }

    public void redo ()
    {
        super.redo ();
        AddDoc.destroy (saved.key (), fromSearchPanel);
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
