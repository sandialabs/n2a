/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.Component;
import java.util.Vector;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;

/**
    Combines UndoableView edits into a single transaction where the view.restore()
    function is only called once for the whole set.

    Edits should all be independent of each other, such that order does not matter.
    The edits will be executed in reverse order, regardless of whether it is redo
    or undo. This way, the first-added edit always executes last, so it has final say over focus.

    Current selection will be cleared, then all edits that can add to selection will do so.
    If this class gets used for more tasks, it may be necessary to make the clear-selection behavior optional.
**/
@SuppressWarnings("serial")
public class CompoundEditView extends CompoundEdit
{
    protected Component            tab;             // We must re-implement tab handling similar to gov.sandia.n2a.ui.Undoable, because we are not a subclass and because we suppress tab handling in our sub-edits.
    protected StoredView           view;
    protected Vector<UndoableEdit> editsBackward;

    /**
        The first edit submitted to this compound provides the stored view object.
        All edits have their stored views removed, so that only this compound does anything
        to re-establish the working view.
        The edits are replayed in exactly the same order they were added, regardless of whether
        the method is redo() or undo(). This means that the last-added edit has final say over focus.
    **/
    public synchronized boolean addEdit (UndoableEdit edit)
    {
        if (! super.addEdit (edit)) return false;
        if (edit instanceof UndoableView)
        {
            UndoableView uv = (UndoableView) edit;
            if (tab == null)  // This is the first-added edit.
            {
                tab  = uv.tab;
                view = uv.view;
            }
            uv.tab  = null;
            uv.view = null;
        }
        return true;
    }

    public void undo () throws CannotUndoException
    {
        MainFrame.instance.tabs.setSelectedComponent (tab);
        view.restore ();
        PanelModel.instance.panelEquations.panelEquationGraph.clearSelection ();

        // undo() normally plays edits back in reverse order.
        // This code circumvents that by substituting a reversed list.
        if (editsBackward == null)
        {
            int last = edits.size ();
            editsBackward = new Vector<UndoableEdit> (last);
            last--;
            for (int i = 0; i <= last; i++) editsBackward.add (edits.get (last - i));
        }
        Vector<UndoableEdit> temp = edits;
        edits = editsBackward;
        super.undo ();
        edits = temp;
    }

    public void redo () throws CannotRedoException
    {
        MainFrame.instance.tabs.setSelectedComponent (tab);
        view.restore ();
        PanelModel.instance.panelEquations.panelEquationGraph.clearSelection ();

        super.redo ();
    }
}
