/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;

public class DeleteInherit extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;  // to parent part
    protected boolean      canceled;
    protected String       value;

    public DeleteInherit (NodeInherit node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path          = container.getKeyPath ();
        this.canceled = canceled;
        value         = node.source.get ();
    }

    public void undo ()
    {
        super.undo ();
        view.restore ();
        AddInherit.create (path, value);
    }

    public void redo ()
    {
        super.redo ();
        view.restore ();
        AddInherit.destroy (path, canceled);
    }
}
