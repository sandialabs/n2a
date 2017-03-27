/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class AddReferences extends Undoable
{
    protected List<String> path;  ///< to parent of $metadata node
    protected int          index; ///< Position within parent node
    protected MVolatile    saved; ///< subtree under $metadata

    public AddReferences (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        saved = new MVolatile ("$references", "");
        saved.merge (data);
    }

    public void undo ()
    {
        super.undo ();
        AddAnnotations.destroy (path, saved.key ());
    }

    public void redo ()
    {
        super.redo ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotations (part);
            }
        };
        AddAnnotations.create (path, index, saved, factory);
    }
}
