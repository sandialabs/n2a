/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
