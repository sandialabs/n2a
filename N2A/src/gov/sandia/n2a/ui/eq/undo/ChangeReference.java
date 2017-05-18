/*
Copyright 2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeReference;

public class ChangeReference extends Undoable
{
    protected List<String> path;  // to the direct parent, whether a $metadata block or a variable
    protected String nameBefore;
    protected String nameAfter;
    protected String valueBefore;
    protected String valueAfter;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeReference (NodeBase container, String nameBefore, String valueBefore, String nameAfter, String valueAfter)
    {
        path = container.getKeyPath ();

        this.nameBefore  = nameBefore;
        this.valueBefore = valueBefore;
        this.nameAfter   = nameAfter;
        this.valueAfter  = valueAfter;
    }

    public void undo ()
    {
        super.undo ();
        ChangeAnnotation.apply (path, nameAfter, valueAfter, nameBefore, valueBefore, "$reference", new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReference (part);
            }
        });
    }

    public void redo ()
    {
        super.redo ();
        ChangeAnnotation.apply (path, nameBefore, valueBefore, nameAfter, valueAfter, "$reference", new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReference (part);
            }
        });
    }
}
