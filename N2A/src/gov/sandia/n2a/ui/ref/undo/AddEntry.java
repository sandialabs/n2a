/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class AddEntry extends Undoable
{
    public String  id;
    public MNode   saved;
    public boolean fromSearchPanel;
    public String  keyAfter;  // key of doc at the location in search list where we should insert the new doc. The other doc will get pushed down a row.
    public boolean wasShowing = true;

    public AddEntry ()
    {
        this ("ref", new MVolatile ());
    }

    public AddEntry (String id, MNode saved)
    {
        this.saved = saved;

        // Determine unique name in database
        MNode references = AppData.docs.child ("references");
        MNode existing = references.child (id);
        if (existing == null)
        {
            this.id = id;
        }
        else
        {
            int suffix = 0;
            while (true)
            {
                if (existing.size () == 0) break;  // no children, so still a virgin
                this.id = id + suffix;
                existing = references.child (this.id);
                if (existing == null) break;
                suffix++;
            }
        }

        PanelReference pr = PanelReference.instance;
        if (pr == null) return;  // We are running headless.
        fromSearchPanel = pr.panelSearch.list.isFocusOwner ();  // Otherwise, assume focus is on the entry panel
        keyAfter = pr.panelSearch.currentKey ();
    }

    public void undo ()
    {
        super.undo ();
        destroy (id, fromSearchPanel);
    }

    public static void destroy (String id, boolean fromSearchPanel)
    {
        AppData.docs.clear ("references", id);  // Triggers PanelReference.childDeleted(name), which removes doc from all 3 sub-panels.

        PanelReference pr = PanelReference.instance;
        if (fromSearchPanel)
        {
            pr.panelSearch.showSelection ();
            pr.panelSearch.list.requestFocusInWindow ();
        }
        // else leave the focus wherever it's at. We shift focus to make user aware of the delete, but this is only meaningful in the search list.
    }

    public void redo ()
    {
        super.redo ();
        int index = 0;
        PanelReference pr = PanelReference.instance;  // If null, then we are running headless.
        if (pr != null  &&  ! keyAfter.isEmpty ())
        {
            index = pr.panelSearch.indexOf (keyAfter);
            if (index < 0) index = 0;
        }
        create (id, saved, index, fromSearchPanel, wasShowing, wasShowing);
    }

    public static void create (String id, MNode saved, int index, boolean fromSearchPanel, boolean wasShowing, boolean willEdit)
    {
        PanelReference pr = PanelReference.instance;
        if (pr != null) pr.panelSearch.insertNextAt (index);

        MNode doc = AppData.docs.childOrCreate ("references", id);  // Triggers PanelReference.childAdded(name), which updates the select and MRU panels, but not the entry panel.
        doc.merge (saved);

        if (pr == null) return;
        if (wasShowing) pr.panelEntry.model.setRecord (doc);
        if (willEdit  ||  ! fromSearchPanel)
        {
            pr.panelSearch.hideSelection ();
            pr.panelSearch.lastSelection = index;  // Because hideSelection() stores current selection, which was one row past index.
            pr.panelEntry.table.requestFocusInWindow ();
        }
        else
        {
            pr.panelSearch.list.setSelectedIndex (index);
            pr.panelSearch.list.requestFocusInWindow ();
        }
    }
}
