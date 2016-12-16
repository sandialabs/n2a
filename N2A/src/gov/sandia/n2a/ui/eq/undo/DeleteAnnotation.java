/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.NodeBase;

public class DeleteAnnotation extends Do
{
    protected List<String> path;  // to parent of $metadata node
    protected int          index; // where to insert among siblings
    protected String       name;
    protected String       value;
    protected boolean      neutralized;

    public DeleteAnnotation (NodeBase node)
    {
        name  = node.source.key ();
        value = node.source.get ();

        NodeBase container = (NodeBase) node.getParent ();
        index = container.getIndex (node);
        if (container.source.key ().equals ("$metadata")) container = (NodeBase) container.getParent ();
        path = container.getKeyPath ();
    }

    public void undo ()
    {
        super.undo ();
        AddAnnotation.create (path, index, name, value);
    }

    public void redo ()
    {
        super.redo ();
        AddAnnotation.destroy (path, name);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddAnnotation  &&  name.equals (((AddAnnotation) edit).name))
        {
            neutralized = true;
            return true;
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}
