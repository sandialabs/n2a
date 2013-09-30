/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class PartInstance {

    public int serialNumber;
    private PartSetInterface pSet;
    private HashMap<String, PartInstance> connectedPIs;
    
    public PartInstance(PartSetInterface pset, int newSerialNumber)
    {
        serialNumber = newSerialNumber;
        pSet = pset;
        connectedPIs = new HashMap<String, PartInstance>();
    }

    public List<Integer> getSNs()
    {
        List<Integer> result = new ArrayList<Integer>();
        result.add(serialNumber);
        if (this instanceof ConnectionInstance)
        {
            ConnectionInstance ci = (ConnectionInstance)this;
            result.add(ci.A.serialNumber);
            result.add(ci.B.serialNumber);
        }
        return result;
    }

    public PartSetInterface getPartSet()
    {
        return this.pSet;
    }

    public void connectPI(String alias, PartInstance pi)
    {
        connectedPIs.put(alias, pi);
    }

    public PartInstance getPI(String alias) 
    {
        return connectedPIs.get(alias);
    }
}
