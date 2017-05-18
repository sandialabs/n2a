/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class ChangeEntry extends Undoable
{
    protected String before;
    protected String after;

    public ChangeEntry (String before, String after)
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
        AppData.references.move (A, B);
        PanelReference pr = PanelReference.instance;
        MNode doc = AppData.references.child (B);
        pr.panelEntry.model.setRecord (doc);  // lazy; only loads if not already loaded
        pr.panelEntry.model.fireTableRowsUpdated (0, 0);  // If we didn't rebuild in previous line, then we need to update display with changed data.
        pr.panelEntry.table.requestFocusInWindow ();  // likewise, focus only moves if it is not already on equation tree
        pr.panelEntry.table.changeSelection (0, 1, false, false);
    }
}
