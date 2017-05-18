/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelReference;

public class AddTag extends Undoable
{
    protected MNode   doc;
    protected int     row;
    protected String  key;
    protected String  value;
    protected boolean nameIsGenerated;

    public AddTag (MNode doc, int row)
    {
        this.doc = doc;
        this.row = row;
        value    = "";
        nameIsGenerated = true;

        MNode record = PanelReference.instance.panelEntry.model.record;
        int suffix = 0;
        while (true)
        {
            key = "k" + suffix++;
            if (record.child (key) == null) break;
        }
    }

    public AddTag (MNode doc, int row, String key, String value)
    {
        this.doc   = doc;
        this.row   = row;
        this.key   = key;
        this.value = value;
        nameIsGenerated = false;
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.destroy (doc, key);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.create (doc, row, key, value, nameIsGenerated);
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof RenameTag)
        {
            RenameTag rename = (RenameTag) edit;
            if (key.equals (rename.before))
            {
                key = rename.after;
                nameIsGenerated = false;
                return true;
            }
        }
        return false;
    }
}
