/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class DeleteAnnotations extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;  ///< to parent of $metadata node
    protected int          index; ///< Position within parent node
    protected MVolatile    saved; ///< subtree under $metadata

    public DeleteAnnotations (NodeBase node)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path  = container.getKeyPath ();
        index = container.getIndex (node);

        saved = new MVolatile ("$metadata", "");
        saved.merge (node.source.getSource ());  // We only save top-document data. $metadata node is guaranteed to be from top doc, due to guard in NodeAnnotations.delete().
    }

    public void undo ()
    {
        super.undo ();
        view.restore ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotations (part);
            }
        };
        AddAnnotations.create (path, index, saved, factory);
    }

    public void redo ()
    {
        super.redo ();
        view.restore ();
        AddAnnotations.destroy (path, saved.key ());
    }
}
