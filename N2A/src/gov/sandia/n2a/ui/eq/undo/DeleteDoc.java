/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;

public class DeleteDoc extends Undoable
{
    protected MVolatile    saved;
    protected boolean      neutralized;
    protected List<String> pathAfter;  // key in the search list immediately after the one being deleted, or null if this is the end of the list
    protected boolean      fromSearchPanel;
    protected boolean      wasShowing;
    protected boolean      wasInMRU;

    public DeleteDoc (MDoc doc)
    {
        saved = new MVolatile (null, doc.key ());
        saved.merge (doc);  // in-memory copy of the entire document

        PanelModel pm = PanelModel.instance;
        fromSearchPanel = pm.panelSearch.tree.isFocusOwner ();
        wasShowing      = pm.panelEquations.record == doc;
        wasInMRU        = pm.panelMRU.hasDoc (doc);
        if (fromSearchPanel) pathAfter = pm.panelSearch.pathAfter (doc.key ());
    }

    public void undo ()
    {
        super.undo ();
        PanelModel mep = PanelModel.instance;
        mep.panelMRU.dontInsert = ! wasInMRU;
        AddDoc.create (saved.key (), saved, pathAfter, fromSearchPanel, wasShowing);
    }

    public void redo ()
    {
        super.redo ();
        AddDoc.destroy (saved.key (), pathAfter, fromSearchPanel);
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
