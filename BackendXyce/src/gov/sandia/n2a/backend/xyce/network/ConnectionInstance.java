/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConnectionInstance extends PartInstance
{
    // TODO - switching to user-specified rather than hard-coded aliases in progress
    public ConnectionInstance(PartSetInterface pset, int serialNumber, Map<String,PartInstance> connections)
    {
        super(pset, serialNumber);
        for (String alias : connections.keySet()) {
            connectPI(alias, connections.get(alias));
        }
    }

    public CompartmentInstance A;
    public CompartmentInstance B;
    
    public ConnectionInstance(
            PartSetInterface pset,
            int serialNumber,
            CompartmentInstance newA,
            CompartmentInstance newB)
    {
        super(pset, serialNumber);
        A = newA;
        B = newB;
        connectPI("A", newA);
        connectPI("B", newB);
    }

    public List<PartInstance> getConnectedInstances()
    {
        List<PartInstance> result = new ArrayList<PartInstance>();
        result.add(A);
        result.add(B);
        return result;
    }
}
