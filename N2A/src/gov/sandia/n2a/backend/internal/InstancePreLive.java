/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class InstancePreLive extends InstanceTemporaries
{
    public InstancePreLive (Instance wrapped, Euler simulator)
    {
        super (wrapped, simulator, true);
    }

    public Type get (Variable v)
    {
        if (v == bed.live) return new Scalar (0);
        return super.get (v);
    }
}
