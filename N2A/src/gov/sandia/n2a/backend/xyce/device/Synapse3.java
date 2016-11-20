/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class Synapse3 extends XyceDevice
{
    String[] nodes = {"", ""};
    String[] modelParams = {"TAU1","TAU2","EREV","VTHRESH","DELAY","GMAX",
            "ALTD", "ALTP", "L1TAU", "L2TAU", "L3TAU", "R", "S",
            "WMIN", "WMAX", "WINIT", "P"};
    String[] ivars = {"A0", "B0", "T0", "W", "VL1", "VL2", "VL3"};

    public Synapse3()
    {
        name = "synapse";
        level = 3;
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
