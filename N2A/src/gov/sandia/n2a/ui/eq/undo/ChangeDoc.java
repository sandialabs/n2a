/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;

public class ChangeDoc extends Undoable
{
    protected String       before;
    protected String       after;
    protected boolean      fromSearchPanel;
    protected List<String> selection;
    protected boolean      wasShowing;

    public ChangeDoc (String before, String after)
    {
        this.before = before;
        this.after  = after;

        PanelModel pm = PanelModel.instance;
        fromSearchPanel = pm.panelSearch.tree.isFocusOwner ()  ||  pm.panelSearch.nameEditor.editor.isFocusOwner ();  // Otherwise, assume focus is on equation panel
        wasShowing      = pm.panelEquations.record != null  &&  pm.panelEquations.record.key ().equals (before);
        if (fromSearchPanel) selection = pm.panelSearch.find (before).getKeyPath ();
    }

    public void undo ()
    {
        super.undo ();
        rename (after, before);
    }

    public void redo ()
    {
        super.redo ();
        rename (before, after);
    }

    public boolean anihilate ()
    {
        return before.equals (after);
    }

    public void rename (String A, String B)
    {
        // Update database
        AppData.models.move (A, B);
        MNode doc = AppData.models.child (B);
        String id = doc.get ("$meta", "id");
        if (! id.isEmpty ()) AppData.set (id, doc);
        if (AppData.state.get ("PanelModel", "lastUsed").equals (A)) AppData.state.set (B, "PanelModel", "lastUsed");

        // Update GUI
        PanelModel pm = PanelModel.instance;
        PanelEquations container = pm.panelEquations;
        if (wasShowing)
        {
            container.load (doc);  // lazy; only loads if not already loaded
            container.root.setUserObject ();  // If it was already loaded, then need to directly update doc name.
        }
        pm.panelMRU.renamed ();  // Because the change in document name does not directly notify the list model.

        if (fromSearchPanel)
        {
            selection.set (selection.size () - 1, B);
            pm.panelSearch.forceSelection (selection);
        }
        else if (wasShowing) container.breadcrumbRenderer.requestFocusInWindow ();
        // else we don't care where focus is
    }
}
