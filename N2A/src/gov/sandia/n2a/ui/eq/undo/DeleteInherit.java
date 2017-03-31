/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;

public class DeleteInherit extends Undoable
{
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
        AddInherit.create (path, value);
    }

    public void redo ()
    {
        super.redo ();
        AddInherit.destroy (path, canceled);
    }
}
