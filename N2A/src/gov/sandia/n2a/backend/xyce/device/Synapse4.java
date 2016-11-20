/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class Synapse4 extends XyceDevice
{
    String[] nodes = {"", ""};
    String[] modelParams = {"TAU1","TAU2","EREV","VTHRESH","DELAY","GMAX"};
    String[] ivars = {"A0", "B0", "T0"};
    
    public Synapse4()
    {
        name = "synapse";
        level = 4;
        defaultNodes = Arrays.asList(nodes);
        modelParameterNames = Arrays.asList(modelParams);
        internalVariables = Arrays.asList(ivars);
    }

    @Override
    public String getCapacitanceVar()
    {
        return "";
    }
}
