/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.NodeFactory;
import gov.sandia.n2a.ui.eq.tree.NodeReference;

public class ChangeReference extends Do
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
        ChangeAnnotation.reverse (path, nameBefore, valueBefore, nameAfter, valueAfter);
    }

    public void redo ()
    {
        super.redo ();
        ChangeAnnotation.forward (path, nameBefore, valueBefore, nameAfter, valueAfter, new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeReference (part);
            }
        });
    }
}
