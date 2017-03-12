/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeReference;
import gov.sandia.n2a.ui.eq.tree.NodeReferences;

public class DeleteReference extends Undoable
{
    protected List<String> path;  // to parent of $reference node
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected String       value;
    protected boolean      neutralized;

    public DeleteReference (NodeBase node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        index = container.getIndex (node);
        if (container.source.key ().equals ("$reference")) container = (NodeBase) container.getParent ();
        path = container.getKeyPath ();
        this.canceled = canceled;

        name  = node.source.key ();
        value = node.source.get ();
    }

    public void undo ()
    {
        super.undo ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReference (part);
            }
        };
        NodeFactory factoryBlock = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReferences (part);
            }
        };
        AddAnnotation.create (path, index, name, value, "$reference", factory, factoryBlock);
    }

    public void redo ()
    {
        super.redo ();
        AddAnnotation.destroy (path, canceled, name, "$reference");
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddReference)
        {
            AddReference ar = (AddReference) edit;
            if (name.equals (ar.name)  &&  ar.value == null)  // null value means the edit has not merged a change node
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
