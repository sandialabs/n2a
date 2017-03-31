/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class DeleteAnnotation extends Undoable
{
    protected List<String> path;  // to parent of $metadata node
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected String       value;
    protected boolean      neutralized;

    public DeleteAnnotation (NodeBase node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        index = container.getIndex (node);
        if (container.source.key ().equals ("$metadata")) container = (NodeBase) container.getParent ();
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
                return new NodeAnnotation (part);
            }
        };
        NodeFactory factoryBlock = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotations (part);
            }
        };
        AddAnnotation.create (path, index, name, value, "$metadata", factory, factoryBlock);
    }

    public void redo ()
    {
        super.redo ();
        AddAnnotation.destroy (path, canceled, name, "$metadata");
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddAnnotation)
        {
            AddAnnotation aa = (AddAnnotation) edit;
            if (path.equals (aa.path)  &&  name.equals (aa.name)  &&  aa.value == null)  // null value means the edit has not merged a change node
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
