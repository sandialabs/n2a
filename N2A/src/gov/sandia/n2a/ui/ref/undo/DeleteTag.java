/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref.undo;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.ref.PanelEntry.MNodeTableModel;
import gov.sandia.n2a.ui.ref.PanelReference;

public class DeleteTag extends Undoable
{
    protected String  key;
    protected int     row;
    protected String  name;
    protected String  value;
    protected boolean neutralized;

    public DeleteTag (MNode doc, String name)
    {
        key       = doc.key ();
        this.name = name;
        MNodeTableModel model = PanelReference.instance.panelEntry.model;
        row       = model.keys.indexOf (name);
        value     = doc.get (name);
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.create (key, row, name, value, false);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.destroy (key, name);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddTag)
        {
            AddTag at = (AddTag) edit;
            if (key == at.key  &&  name.equals (at.name))
            {
                neutralized = true;
                return true;
            }
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}
