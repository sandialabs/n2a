/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.NodeFactory;
import gov.sandia.n2a.ui.eq.tree.NodeReferences;

public class DeleteReferences extends Do
{
    protected List<String> path;  ///< to parent of $metadata node
    protected int          index; ///< Position within parent node
    protected MVolatile    saved; ///< subtree under $metadata

    public DeleteReferences (NodeBase node)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path  = container.getKeyPath ();
        index = container.getIndex (node);

        saved = new MVolatile ("$references", "");
        saved.merge (node.source.getSource ());  // We only save top-document data. $metadata node is guaranteed to be from top doc, due to guard in NodeAnnotations.delete().
    }

    public void undo ()
    {
        super.undo ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReferences (part);
            }
        };
        DeleteAnnotations.create (path, index, saved, factory);
    }

    public void redo ()
    {
        super.redo ();
        DeleteAnnotations.destroy (path, saved);
    }
}
