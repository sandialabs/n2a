/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;

public class DeleteInherit extends UndoableView
{
    protected List<String> path;  // to parent part
    protected boolean      canceled;
    protected String       value;
    protected boolean      neutralized;

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

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddInherit)
        {
            AddInherit ai = (AddInherit) edit;
            neutralized = path.equals (ai.path);
        }
        return neutralized;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}
