/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class ChangeTag extends Undoable
{
    protected String key;  // of document containing the tag
    protected String name;
    protected String before;
    protected String after;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeTag (MNode doc, String name, String value)
    {
        key       = doc.key ();
        this.name = name;
        before    = doc.get (name);
        after     = value;
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.changeValue (key, name, before);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.changeValue (key, name, after);
    }
}
