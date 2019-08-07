/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    public String  name;
    public MNode   saved;
    public boolean fromSearchPanel;
    public String  keyAfter;  // key of doc at the location in search list where we should insert the new doc. The other doc will get pushed down a row.
    public boolean wasShowing = true;

    public AddDoc ()
    {
        this ("New Model", new MVolatile ());
    }

    public AddDoc (String name, MNode saved)
    {
        this.name  = uniqueName (name);
        this.saved = saved;

        PanelModel mep = PanelModel.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();  // Otherwise, assume focus is on equation tree
        keyAfter = mep.panelSearch.currentKey ();

        // Insert ID, if given doc does not already have one.
        MNode id = saved.childOrCreate ("$metadata", "id");
        String idString = id.get ();
        if (idString.isEmpty ()  ||  AppData.getModel (idString) != null) id.set (generateID ());
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

    public static void destroy (String name, boolean fromSearchPanel)
    {
        MNode doc = AppData.models.child (name);
        String id = doc.get ("$metadata", "id");
        if (! id.isEmpty ()) AppData.set (id, null);
        AppData.models.clear (name);  // Triggers PanelModel.childDeleted(name), which removes doc from all 3 sub-panels.

        PanelModel pm = PanelModel.instance;
        if (fromSearchPanel)
        {
            pm.panelSearch.showSelection ();
            pm.panelSearch.list.requestFocusInWindow ();
        }
        // else leave the focus wherever it's at. We shift focus to make user aware of the delete, but this is only meaningful in the search list.
    }

    public void redo ()
    {
        super.redo ();
        int index = 0;
        if (! keyAfter.isEmpty ())
        {
            index = PanelModel.instance.panelSearch.indexOf (keyAfter);
            if (index < 0) index = 0;
        }
        create (name, saved, index, fromSearchPanel, wasShowing, wasShowing);
    }

    public static void create (String name, MNode saved, int index, boolean fromSearchPanel, boolean wasShowing, boolean willEdit)
    {
        PanelModel pm = PanelModel.instance;
        pm.panelSearch.insertNextAt (index);

        MDoc doc = (MDoc) AppData.models.childOrCreate (name);  // Triggers PanelModel.childAdded(name), which updates the select and MRU panels, but not the equation tree panel.
        doc.merge (saved);
        new MPart (doc).clearRedundantOverrides ();
        AppData.set (doc.get ("$metadata", "id"), doc);

        if (wasShowing) pm.panelEquations.load (doc);  // Takes focus
        pm.panelSearch.lastSelection = index;
        if (fromSearchPanel)
        {
            pm.panelSearch.list.setSelectedIndex (index);
            pm.panelSearch.list.requestFocusInWindow ();
        }
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
