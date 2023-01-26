/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class DeleteReference extends UndoableView
{
    protected List<String> path;  // to parent of $ref node
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected String       value;
    protected boolean      neutralized;
    protected boolean      multi;
    protected boolean      multiLast;

    public DeleteReference (NodeBase node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        index         = container.getIndex (node);
        if (container.source.key ().equals ("$ref")) container = (NodeBase) container.getParent ();
        path          = container.getKeyPath ();
        this.canceled = canceled;

        name  = node.source.key ();
        value = node.source.get ();
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void setMultiLast (boolean value)
    {
        multiLast = value;
    }

    public void undo ()
    {
        super.undo ();
        AddReference.create (path, index, name, value, multi);
    }

    public void redo ()
    {
        super.redo ();
        AddReference.destroy (path, canceled, name, ! multi  ||  multiLast);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddReference)
        {
            AddReference ar = (AddReference) edit;
            if (path.equals (ar.path)  &&  name.equals (ar.name)  &&  ar.value == null)  // null value means the edit has not merged a change node
            {
                neutralized = true;
                return true;
            }
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}
