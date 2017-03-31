/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

public class Undoable implements UndoableEdit
{
    protected boolean hasBeenDone = false;  // In the original AbstractUndoableEdit class, this was initialized true. We set it false and expect the caller to run redo() during creation of this object.
    protected boolean alive       = true;

    public void die ()
    {
        alive = false;
    }

    public void undo () throws CannotUndoException
    {
        if (! canUndo ()) throw new CannotUndoException ();
        hasBeenDone = false;
    }

    public boolean canUndo ()
    {
        return alive && hasBeenDone;
    }

    public void redo () throws CannotRedoException
    {
        if (! canRedo ()) throw new CannotRedoException();
        hasBeenDone = true;
    }

    public boolean canRedo ()
    {
        return alive && ! hasBeenDone;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        return false;
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        return false;
    }

    /**
        Indicates that this edit has become null and thus should be removed.
        This can happen if it incorporates two edits that exactly neutralized each other, such as an add followed by a delete.
    **/
    public boolean anihilate ()
    {
        return false;
    }

    public boolean isSignificant ()
    {
        return true;
    }

    public String getPresentationName ()
    {
        return "";
    }

    public String getUndoPresentationName ()
    {
        return "Undo " + getPresentationName ();
    }

    public String getRedoPresentationName ()
    {
        return "Redo " + getPresentationName ();
    }
}
