/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import javax.swing.undo.UndoableEdit;

public class CompoundEdit extends javax.swing.undo.CompoundEdit
{
    /**
        Implements anihilate on incoming edits.
        Assumes that given edit has already been redone by our own extended UndoManager.
    **/
    public synchronized boolean addEdit (UndoableEdit edit)
    {
        if (! super.addEdit (edit)) return false;
        if (edit instanceof Undoable  &&  ((Undoable) edit).anihilate ())
        {
            edits.remove (edits.size () - 1);
        }
        return true;
    }

    public synchronized boolean isEmpty ()
    {
        return edits.size () == 0;
    }
}
