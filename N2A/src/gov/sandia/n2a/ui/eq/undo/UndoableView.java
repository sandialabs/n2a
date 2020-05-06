/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;

public class UndoableView extends Undoable
{
    protected StoredView view;

    public UndoableView ()
    {
        view = PanelModel.instance.panelEquations.new StoredView ();
    }

    public UndoableView (NodeBase node)
    {
        view = PanelModel.instance.panelEquations.new StoredView (node);
    }

    public void setMulti (boolean value)
    {
    }

    /**
        For deletes or delete-like operations, sets a flag so that indicates this is the last
        item in a compound edit. In this case, it is necessary to determine where the focus
        should go after the node disappears. It is also possible that an overridden node
        becomes visible, so the focus should go nowhere. Individual undoable classes will know
        what to do.
    **/
    public void setMultiLast (boolean value)
    {
    }

    public void undo () throws CannotUndoException
    {
        super.undo ();
        if (view != null) view.restore ();
    }

    public void redo () throws CannotRedoException
    {
        super.redo ();
        if (view != null) view.restore ();
    }
}
