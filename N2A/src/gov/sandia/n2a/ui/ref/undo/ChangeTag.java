/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class ChangeTag extends Undoable
{
    protected MNode  doc;
    protected String key;
    protected String before;
    protected String after;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeTag (MNode doc, String key, String value)
    {
        this.doc = doc;
        this.key = key;
        before   = PanelReference.instance.panelEntry.model.record.get (key);
        after    = value;
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.changeValue (doc, key, before);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.changeValue (doc, key, after);
    }
}
