/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class InstancePreLive extends InstanceTemporaries
{
    public InstancePreLive (Instance wrapped, Simulator simulator)
    {
        super (wrapped, simulator, true);
    }

    public Type get (Variable v)
    {
        if (v == bed.live) return new Scalar (0);
        return super.get (v);
    }
}
