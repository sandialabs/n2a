/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.search.NodeBase;

public class AddDoc extends Undoable
{
    public String       name;
    public boolean      nameIsGenerated;
    public MNode        saved;
    public boolean      fromSearchPanel;
    public List<String> pathAfter;  // tree path of doc at the location in search list where we should insert the new doc. The other doc will get pushed down a row.
    public boolean      wasShowing = true;

    public AddDoc ()
    {
        this ("New Model", new MVolatile ());
        nameIsGenerated = true;
    }

    public AddDoc (String name, MNode saved)
    {
        this.name  = uniqueName (MDir.validFilenameFrom (name));
        this.saved = saved;

        PanelModel pm   = PanelModel.instance;
        JTree      tree = pm.panelSearch.tree;
        fromSearchPanel = tree.isFocusOwner ();  // Otherwise, assume focus is on equation tree
        if (fromSearchPanel)  // Otherwise, pathAfter is null and new entry will appear at top of uncategorized entries.
        {
            NodeBase n = pm.panelSearch.getSelectedNode ();
            if (n != null)
            {
                pathAfter = n.getKeyPath ();
                if (tree.isExpanded (new TreePath (n.getPath ()))) pathAfter.add ("");
                List<String> path = pathAfter.subList (0, pathAfter.size () - 1);

                String category = "";
                for (String c : path) category += "/" + c;
                if (! category.isEmpty ())
                {
                    category = category.substring (1);
                    saved.set (category, "$metadata", "gui", "category");
                }
            }
        }

        // Insert ID, if given doc does not already have one.
        MNode id = saved.childOrCreate ("$metadata", "id");
        String idString = id.get ();
        if (idString.isEmpty ()  ||  AppData.getModel (idString) != null) id.set (generateID ());
    }

    public void setSilent ()
    {
        wasShowing      = false;
        fromSearchPanel = false;
        pathAfter       = null;
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
        destroy (name, pathAfter, fromSearchPanel);
    }

    public static void destroy (String name, List<String> pathAfter, boolean fromSearchPanel)
    {
        MNode doc = AppData.models.child (name);
        String id = doc.get ("$metadata", "id");
        if (! id.isEmpty ()) AppData.set (id, null);
        AppData.models.clear (name);  // Triggers PanelModel.childDeleted(name), which removes doc from all 3 sub-panels.

        PanelModel pm = PanelModel.instance;
        if (fromSearchPanel) pm.panelSearch.forceSelection (pathAfter);
        // else leave the focus wherever it's at. We shift focus to make user aware of the delete, but this is only meaningful in the search list.
    }

    public void redo ()
    {
        super.redo ();
        create (name, saved, pathAfter, fromSearchPanel, wasShowing);
    }

    public static void create (String name, MNode saved, List<String> pathAfter, boolean fromSearchPanel, boolean wasShowing)
    {
        PanelModel pm = PanelModel.instance;
        pm.panelSearch.insertNextAt (pathAfter);  // Note that lastSelection will end up pointing to new entry, not pathAfter.

        MDoc doc = (MDoc) AppData.models.childOrCreate (name);  // Triggers PanelModel.childAdded(name), which updates the select and MRU panels, but not the equation tree panel.
        doc.merge (saved);
        new MPart (doc).clearRedundantOverrides ();
        AppData.set (doc.get ("$metadata", "id"), doc);
        if (doc.get ("$metadata", "gui", "category").contains (",")) pm.panelSearch.search ();  // update for multiple categories

        if (wasShowing) pm.panelEquations.load (doc);  // Takes focus
        if (fromSearchPanel)
        {
            pm.panelSearch.tree.clearSelection ();
            if (wasShowing) pm.panelSearch.tree.requestFocusInWindow ();  // For some reason tree still thinks it has the focus, so takeFocus() doesn't work correctly. This call forces it back.
            else            pm.panelSearch.takeFocus ();
        }
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangeDoc)
        {
            ChangeDoc change = (ChangeDoc) edit;
            if (name.equals (change.before))
            {
                name = change.after;
                nameIsGenerated = false;
                return true;
            }
        }
        return false;
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
