/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

public class UndoManager extends javax.swing.undo.UndoManager
{
    public synchronized boolean add (Undoable edit)
    {
        edit.redo ();  // All descendants of Do are expected to carry out their operation once on creation. We do that here for convenience.

        if (! super.addEdit (edit)) return false;
        if (edit.anihilate ())
        {
            int last = edits.size () - 1;
            trimEdits (last, last);  // We have to do this indirectly because we can't directly maintain indexOfNextAdd.
        }
        return true;
    }

    public Undoable editToBeUndone ()
    {
        return (Undoable) super.editToBeUndone ();
    }
}
