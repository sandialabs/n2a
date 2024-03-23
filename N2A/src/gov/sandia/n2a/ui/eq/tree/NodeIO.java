/*
Copyright 2016-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MPartRepo;
import gov.sandia.n2a.db.MVolatile;

@SuppressWarnings("serial")
public class NodeIO extends NodePart
{
    protected String side;

    public NodeIO (String side, NodePart parent)
    {
        this.side    = side;
        this.parent  = parent;  // No real hierarchy established. In particular, container.part does not get node added to its children.
        iconCustom16 = NodeVariable.iconBinding;

        MNode empty;
        if (side.equals ("in"))
        {
            empty = new MVolatile (null, "Inputs");
            pinOut      = parent.pinIn;
            pinOutOrder = parent.pinInOrder;
        }
        else
        {
            empty = new MVolatile (null, "Outputs");
            pinIn      = parent.pinOut;
            pinInOrder = parent.pinOutOrder;
        }
        source = new MPartRepo (empty);  // fake source
    }

    @Override
    public int getForegroundColor ()
    {
        NodePart np = (NodePart) trueParent;
        MPart pin = (MPart) np.source.child ("$meta", "gui", "pin", side);
        if (pin != null  &&  pin.isFromTopDocument ()) return OVERRIDE;
        return INHERIT;
    }
}
