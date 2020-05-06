/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class DeleteVariable extends UndoableView
{
    protected List<String> path;  // to variable node
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected MNode        savedSubtree;
    protected boolean      neutralized;
    protected boolean      multi;
    protected boolean      multiLast;

    public DeleteVariable (NodeVariable node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path          = container.getKeyPath ();
        index         = container.getIndex (node);
        this.canceled = canceled;
        name          = node.source.key ();

        savedSubtree = new MVolatile ();
        savedSubtree.merge (node.source.getSource ());
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
        AddVariable.create (path, index, name, savedSubtree, false, multi);
    }

    public void redo ()
    {
        super.redo ();
        AddVariable.destroy (path, canceled, name, ! multi  ||  multiLast);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddVariable)
        {
            AddVariable av = (AddVariable) edit;
            if (path.equals (av.path)  &&  name.equals (av.name)  &&  av.nameIsGenerated)
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
