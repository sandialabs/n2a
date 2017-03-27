/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.ModelEditPanel;

public class AddDoc extends Undoable
{
    protected String  name;
    protected boolean fromSearchPanel;
    protected int     index;  // 0 by default
    protected MNode   saved;

    public AddDoc ()
    {
        this ("New Model", new MVolatile ());
    }

    public AddDoc (String name)
    {
        this (name, new MVolatile ());
    }

    public AddDoc (String name, MNode saved)
    {
        this.saved = saved;

        ModelEditPanel mep = ModelEditPanel.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();  // Otherwise, assume focus is on equation tree

        // Determine unique name in database
        MNode models = AppData.models;
        MNode existing = models.child (name);
        if (existing == null)
        {
            this.name = name;
        }
        else
        {
            name += " ";
            int suffix = 2;
            while (true)
            {
                if (existing.length () == 0) break;  // no children, so still a virgin
                this.name = name + suffix;
                existing = models.child (this.name);
                if (existing == null) break;
                suffix++;
            }
        }
    }

    public void undo ()
    {
        super.undo ();
        destroy (name, fromSearchPanel);
    }

    public static int destroy (String name, boolean fromSearchPanel)
    {
        MNode doc = AppData.models.child (name);
        ModelEditPanel mep = ModelEditPanel.instance;
        mep.panelEquations.recordDeleted (doc);
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
            mep.panelEquations.tree.requestFocusInWindow ();
        }
        return result;
    }

    public void redo ()
    {
        super.redo ();
        create (name, saved, 0, fromSearchPanel, true);
    }

    public static int create (String name, MNode saved, int index, boolean fromSearchPanel, boolean wasShowing)
    {
        MNode doc = AppData.models.set (name, "");
        doc.merge (saved);
        ModelEditPanel mep = ModelEditPanel.instance;
        int result = mep.panelSearch.insertDoc (doc, index);
        if (wasShowing) mep.panelEquations.loadRootFromDB (doc);
        mep.panelSearch.lastSelection = index;
        if (fromSearchPanel)
        {
            if (wasShowing) mep.panelEquations.tree.clearSelection ();
            mep.panelSearch.list.setSelectedIndex (result);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEquations.tree.requestFocusInWindow ();
        }
        return result;
    }
}
