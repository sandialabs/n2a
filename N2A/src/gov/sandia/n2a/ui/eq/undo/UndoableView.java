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
    protected StoredView view = PanelModel.instance.panelEquations.new StoredView ();

    public UndoableView ()
    {
        view = PanelModel.instance.panelEquations.new StoredView ();
    }

    public UndoableView (NodeBase node)
    {
        view = PanelModel.instance.panelEquations.new StoredView (node);
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
