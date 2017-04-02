/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class AddRef extends Undoable
{
    protected String  id;
    protected boolean fromSearchPanel;
    protected int     index;  // 0 by default
    protected MNode   saved;
    public    boolean wasShowing = true;

    public AddRef ()
    {
        this ("ref", new MVolatile ());
    }

    public AddRef (String id)
    {
        this (id, new MVolatile ());
    }

    public AddRef (String id, MNode saved)
    {
        this.saved = saved;

        PanelReference mep = PanelReference.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();  // Otherwise, assume focus is on the entry panel

        // Determine unique name in database
        MNode references = AppData.references;
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
                if (existing.length () == 0) break;  // no children, so still a virgin
                this.id = id + suffix;
                existing = references.child (this.id);
                if (existing == null) break;
                suffix++;
            }
        }
    }

    public void undo ()
    {
        super.undo ();
        destroy (id, fromSearchPanel);
    }

    public static int destroy (String id, boolean fromSearchPanel)
    {
        MNode doc = AppData.references.child (id);
        PanelReference mep = PanelReference.instance;
        mep.panelEntry.recordDeleted (doc);
        mep.panelMRU.removeDoc (doc);
        int result = mep.panelSearch.removeDoc (doc);
        ((MDoc) doc).delete ();
        mep.panelSearch.lastSelection = Math.min (mep.panelSearch.model.size () - 1, result);
        if (fromSearchPanel)
        {
            mep.panelSearch.list.setSelectedIndex (mep.panelSearch.lastSelection);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEntry.table.requestFocusInWindow ();
        }
        return result;
    }

    public void redo ()
    {
        super.redo ();
        create (id, saved, 0, fromSearchPanel, wasShowing);
    }

    public static int create (String id, MNode saved, int index, boolean fromSearchPanel, boolean wasShowing)
    {
        MNode doc = AppData.references.set (id, "");
        doc.merge (saved);
        PanelReference mep = PanelReference.instance;
        mep.panelMRU.insertDoc (doc);
        int result = mep.panelSearch.insertDoc (doc, index);
        if (wasShowing) mep.panelEntry.model.setRecord (doc);
        mep.panelSearch.lastSelection = index;
        if (fromSearchPanel)
        {
            if (wasShowing) mep.panelEntry.table.clearSelection ();
            mep.panelSearch.list.setSelectedIndex (result);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEntry.table.requestFocusInWindow ();
        }
        return result;
    }
}
