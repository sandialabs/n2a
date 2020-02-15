/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    protected String  key;
    protected int     row;
    protected String  name;
    protected String  value;
    protected boolean nameIsGenerated;

    public AddTag (MNode doc, int row)
    {
        key      = doc.key ();
        this.row = row;
        value    = "";
        nameIsGenerated = true;

        int suffix = 0;
        while (true)
        {
            name = "k" + suffix++;
            if (doc.child (name) == null) break;
        }
    }

    public AddTag (MNode doc, int row, String name, String value)
    {
        key        = doc.key ();
        this.row   = row;
        this.name  = name;
        this.value = value;
        nameIsGenerated = false;
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.destroy (key, name);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.create (key, row, name, value, nameIsGenerated);
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof RenameTag)
        {
            RenameTag rename = (RenameTag) edit;
            if (name.equals (rename.before))
            {
                name = rename.after;
                nameIsGenerated = false;
                return true;
            }
        }
        return false;
    }
}
