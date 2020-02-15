/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class DeleteEntry extends Undoable
{
    protected MVolatile saved;
    protected boolean   neutralized;
    protected String    keyAfter;  // key of the doc in the search list immediately after the one being deleted, or empty string is this is the end of the list
    protected boolean   fromSearchPanel;
    protected boolean   wasShowing;
    protected boolean   wasInMRU;

    public DeleteEntry (MDoc doc)
    {
        String key = doc.key ();
        saved = new MVolatile (null, key);
        saved.merge (doc);  // in-memory copy of the entire document

        PanelReference pr = PanelReference.instance;
        fromSearchPanel = pr.panelSearch.list.isFocusOwner ();
        keyAfter        = pr.panelSearch.keyAfter (key);
        wasShowing      = pr.panelEntry.model.record == doc;
        wasInMRU        = pr.panelMRU.hasDoc (doc);
    }

    public void undo ()
    {
        super.undo ();
        PanelReference pr = PanelReference.instance;
        pr.panelMRU.dontInsert = ! wasInMRU;
        int index = pr.panelSearch.indexOf (keyAfter);
        if (index < 0) index = 0;
        AddEntry.create (saved.key (), saved, index, fromSearchPanel, wasShowing, false);
    }

    public void redo ()
    {
        super.redo ();
        AddEntry.destroy (saved.key (), fromSearchPanel);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddEntry  &&  saved.key ().equals (((AddEntry) edit).id))
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
