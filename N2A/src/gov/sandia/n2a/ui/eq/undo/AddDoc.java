/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;

public class AddDoc extends Undoable
{
    public    String  name;  // public so we can use the name in a potential Outsource operation
    protected boolean fromSearchPanel;
    protected int     index;  // 0 by default
    public    MNode   saved;  // public so we can fill it in with imported model after creating an instance of this class
    public    boolean wasShowing = true;

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
        this.name  = uniqueName (name);
        this.saved = saved;

        PanelModel mep = PanelModel.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();  // Otherwise, assume focus is on equation tree

        // Insert ID, if given doc does not already have one.
        MNode id = saved.childOrCreate ("$metadata", "id");
        if (id.get ().isEmpty ()  ||  ! this.name.equals (name)) id.set (generateID ());
    }

    /**
        Determine unique name in database
    **/
    public static String uniqueName (String name)
    {
        MNode models = AppData.models;
        MNode existing = models.child (name);
        if (existing == null) return name;

        String result = name;
        name += " ";
        int suffix = 2;
        while (true)
        {
            if (existing.size () == 0) return result;  // no children, so still a virgin
            result = name + suffix;
            existing = models.child (result);
            if (existing == null) return result;
            suffix++;
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
        PanelModel mep = PanelModel.instance;
        mep.panelEquations.recordDeleted (doc);
        mep.panelMRU.removeDoc (doc);
        int result = mep.panelSearch.removeDoc (doc);
        String id = doc.get ("$metadata", "id");
        if (! id.isEmpty ()) AppData.set (id, null);
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
        create (name, saved, 0, fromSearchPanel, wasShowing);
    }

    public static int create (String name, MNode saved, int index, boolean fromSearchPanel, boolean wasShowing)
    {
        MDoc doc = (MDoc) AppData.models.set (name, "");
        doc.merge (saved);
        new MPart (doc).clearRedundantOverrides ();
        AppData.set (doc.get ("$metadata", "id"), doc);

        PanelModel mep = PanelModel.instance;
        mep.panelMRU.insertDoc (doc);
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

 
    // ID generation -------------------------------------------------------

    protected static long userHash = ((long) System.getProperty ("user.name").hashCode ()) << 48 & 0x7FFFFFFFFFFFFFFFl;
    protected static long IDcount;

    public static synchronized String generateID ()
    {
        long time = System.currentTimeMillis () << 8 & 0x7FFFFFFFFFFFFFFFl;
        return Long.toHexString (userHash | time | IDcount++);
    }
}
