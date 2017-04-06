/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class ChangeRef extends Undoable
{
    protected String before;
    protected String after;

    public ChangeRef (String before, String after)
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
        PanelReference mep = PanelReference.instance;
        MNode doc = AppData.references.child (B);
        mep.panelEntry.model.setRecord (doc);  // lazy; only loads if not already loaded
        mep.panelEntry.model.fireTableRowsUpdated (0, 0);  // If we didn't rebuild in previous line, then we need to update display with changed data.
        mep.panelEntry.table.requestFocusInWindow ();  // likewise, focus only moves if it is not already on equation tree
        mep.panelEntry.table.changeSelection (0, 1, false, false);
    }
}
