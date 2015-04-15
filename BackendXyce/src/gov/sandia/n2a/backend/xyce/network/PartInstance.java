/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

public abstract class PartInstance {

    public int serialNumber;
    private PartSetInterface pSet;
    
    public PartInstance(PartSetInterface pset, int newSerialNumber)
    {
        serialNumber = newSerialNumber;
        pSet = pset;
    }

    public PartSetInterface getPartSet()
    {
        return this.pSet;
    }
}
