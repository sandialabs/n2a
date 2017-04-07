/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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
    protected boolean nameIsGenerated;

    public AddTag (MNode doc, int row)
    {
        this.doc = doc;
        this.row = row;
        nameIsGenerated = true;

        MNode record = PanelReference.instance.panelEntry.model.record;
        int suffix = 0;
        while (true)
        {
            key = "k" + suffix++;
            if (record.child (key) == null) break;
        }
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.destroy (doc, key);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.create (doc, row, key, "", nameIsGenerated);
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
