/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui;

import javax.swing.undo.UndoableEdit;

public class UndoManager extends javax.swing.undo.UndoManager
{
    public synchronized boolean add (Undoable edit)
    {
        edit.redo ();  // All descendants of Undoable are expected to carry out their operation once on creation. We do that here for convenience.

        if (! super.addEdit (edit)) return false;
        UndoableEdit lastEdit = lastEdit ();  // lastEdit could be a CompoundEdit, thus we have to check ...
        if (lastEdit instanceof Undoable  &&  ((Undoable) lastEdit).anihilate ())
        {
            int lastIndex = edits.size () - 1;
            trimEdits (lastIndex, lastIndex);  // We have to do this indirectly because indexOfNextAdd is package private, so we can't maintain it.
        }
        return true;
    }

    public synchronized void endCompoundEdit ()
    {
        UndoableEdit lastEdit = lastEdit ();
        if (lastEdit instanceof CompoundEdit)
        {
            CompoundEdit compound = (CompoundEdit) lastEdit;
            compound.end ();
            if (compound.isEmpty ())
            {
                // Remove it. This is not strictly necessary, because an empty CompoundEdit will return false for isSignificant(), and thus not add a step for the user.
                int lastIndex = edits.size () - 1;
                trimEdits (lastIndex, lastIndex);
            }
        }
    }
}
