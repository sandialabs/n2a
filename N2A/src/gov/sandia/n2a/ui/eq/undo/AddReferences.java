/*
Copyright 2017,2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeReferences;

public class AddReferences extends UndoableView
{
    protected List<String> path;  ///< to parent of $metadata node
    protected int          index; ///< Position within parent node
    protected MVolatile    saved; ///< subtree under $metadata

    public AddReferences (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        saved = new MVolatile (null, "$references");
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
                return new NodeReferences (part);
            }
        };
        AddAnnotations.create (path, index, saved, factory);
    }
}
