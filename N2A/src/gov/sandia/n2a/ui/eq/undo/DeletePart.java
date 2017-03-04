/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class DeletePart extends Undoable
{
    protected List<String> path;  // to containing part
    protected int          index; // where to insert among siblings
    protected MNode        savedSubtree;
    protected boolean      neutralized;

    public DeletePart (NodePart node)
    {
        savedSubtree = new MVolatile (node.source.key (), "");
        savedSubtree.merge (node.source);

        NodeBase container = (NodeBase) node.getParent ();
        index = container.getIndex (node);
        path  = container.getKeyPath ();
    }

    public void undo ()
    {
        super.undo ();
        AddPart.create (path, index, savedSubtree, false);
    }

    public void redo ()
    {
        super.redo ();
        AddPart.destroy (path, savedSubtree.key ());
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddPart)
        {
            AddPart ap = (AddPart) edit;
            if (path.equals (ap.path)  &&  savedSubtree.key ().equals (ap.createSubtree.key ())  &&  ap.nameIsGenerated)
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
