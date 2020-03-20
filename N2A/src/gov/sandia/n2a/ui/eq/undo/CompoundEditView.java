/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.Vector;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;

/**
    Combines UndoableView edits into a single transaction where the view.restore()
    function is only called once for the whole set.
    Edits should all be independent of each other, such that order does not matter.
    The edits will be executed in reverse order, regardless of whether it is redo
    or undo. This way, the lead edit always executes last, so it has final say over focus.
**/
@SuppressWarnings("serial")
public class CompoundEditView extends CompoundEdit
{
    protected StoredView           view;
    protected Vector<UndoableEdit> editsBackward;

    /**
        The first edit submitted to this compound provides the stored view object.
        All edits have their stored views removed, so that only this compound does anything
        to re-establish the working view. The first submitted edit should be the one that
        you want to receive the final focus after a do or undo.
    **/
    public synchronized boolean addEdit (UndoableEdit edit)
    {
        if (! super.addEdit (edit)) return false;
        if (edit instanceof UndoableView)
        {
            UndoableView uv = (UndoableView) edit;
            if (view == null) view = uv.view;
            uv.view = null;
        }
        return true;
    }

    public void undo () throws CannotUndoException
    {
        view.restore ();  // Must be done first.
        super.undo ();    // Process all edits in compound.
    }

    public void redo () throws CannotRedoException
    {
        view.restore ();

        // Apply the edits in reverse order, just as in undo().
        if (editsBackward == null)
        {
            int last = edits.size ();
            editsBackward = new Vector<UndoableEdit> (last);
            last--;
            for (int i = 0; i <= last; i++) editsBackward.add (edits.get (last - i));
        }
        Vector<UndoableEdit> temp = edits;
        edits = editsBackward;
        super.redo ();
        edits = temp;
    }
}
