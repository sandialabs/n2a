/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangeDoc extends Undoable
{
    protected String before;
    protected String after;

    public ChangeDoc (String before, String after)
    {
        this.before = before;
        this.after  = after;
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

    public static void rename (String A, String B)
    {
        // Update database
        AppData.models.move (A, B);
        MNode doc = AppData.models.child (B);
        String id = doc.get ("$metadata", "id");
        if (! id.isEmpty ()) AppData.set (id, doc);

        // Update GUI
        PanelModel mep = PanelModel.instance;
        PanelEquations container = mep.panelEquations;
        container.load (doc);  // lazy; only loads if not already loaded
        NodePart root = container.root;
        root.setUserObject ();
        container.takeFocus ();
        if (container.viewTree)
        {
            container.panelEquationTree.model.nodeChanged (root);
            container.panelEquationTree.tree.setSelectionRow (0);
        }
        mep.panelMRU.renamed ();  // Because the change in document name does not directly notify the list model.
        mep.panelSearch.list.repaint ();
    }
}
