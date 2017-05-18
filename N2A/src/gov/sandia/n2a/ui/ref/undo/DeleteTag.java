/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    protected MNode   doc;
    protected int     row;
    protected String  key;
    protected String  value;
    protected boolean neutralized;

    public DeleteTag (MNode doc, int row)
    {
        this.doc = doc;
        this.row = row;
        MNodeTableModel model = PanelReference.instance.panelEntry.model;
        key   = model.keys.get (row);
        value = model.record.get (key);
    }

    public DeleteTag (MNode doc, String key)
    {
        this.doc = doc;
        this.key = key;
        MNodeTableModel model = PanelReference.instance.panelEntry.model;
        row   = model.keys.indexOf (key);
        value = model.record.get (key);
    }

    public void undo ()
    {
        super.undo ();
        PanelReference.instance.panelEntry.model.create (doc, row, key, value, false);
    }

    public void redo ()
    {
        super.redo ();
        PanelReference.instance.panelEntry.model.destroy (doc, key);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddTag)
        {
            AddTag af = (AddTag) edit;
            if (doc.equals (af.doc)  &&  key.equals (af.key)  &&  af.nameIsGenerated)
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
