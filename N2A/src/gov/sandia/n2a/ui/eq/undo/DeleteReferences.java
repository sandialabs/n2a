/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeReferences;

public class DeleteReferences extends UndoableView
{
    protected List<String> path;  ///< to parent of $meta node
    protected int          index; ///< Position within parent node
    protected MVolatile    saved; ///< subtree under $meta
    protected boolean      multi;
    protected boolean      multiLast;

    public DeleteReferences (NodeBase node)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path  = container.getKeyPath ();
        index = container.getIndex (node);

        saved = new MVolatile (null, "$ref");
        saved.merge (node.source.getSource ());  // We only save top-document data. $meta node is guaranteed to be from top doc, due to guard in NodeAnnotations.delete().
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
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReferences (part);
            }
        };
        AddAnnotations.create (path, index, saved, factory, multi, false, false);
    }

    public void redo ()
    {
        super.redo ();
        AddAnnotations.destroy (path, saved.key (), ! multi  ||  multiLast, false, false);
    }
}
