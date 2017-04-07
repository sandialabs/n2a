/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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
    protected int       index;  ///< Position of this doc in the search panel before it was deleted.
    protected boolean   fromSearchPanel;
    protected boolean   wasShowing;

    public DeleteEntry (MDoc doc)
    {
        saved = new MVolatile (doc.key (), "");
        saved.merge (doc);  // in-memory copy of the entire document

        PanelReference mep = PanelReference.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();
        index           = mep.panelSearch.indexOf (doc);
        wasShowing      = mep.panelEntry.model.record == doc;
    }

    public void undo ()
    {
        super.undo ();
        AddEntry.create (saved.key (), saved, index, fromSearchPanel, wasShowing);
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
