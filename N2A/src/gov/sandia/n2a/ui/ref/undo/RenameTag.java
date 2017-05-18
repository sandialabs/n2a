/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class RenameTag extends Undoable
{
    protected MNode  doc;
    protected int    exposedRow;
    protected String before;
    protected String after;

    public RenameTag (MNode doc, int exposedRow, String before, String after)
    {
        this.doc        = doc;
        this.exposedRow = exposedRow;
        this.before     = before;
        this.after      = after;
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.rename (doc, exposedRow, after, before);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.rename (doc, exposedRow, before, after);
    }
}
