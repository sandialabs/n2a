/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class Neuron7XyceDevice extends XyceDevice
{
    String[] nodes = {""};
    String[] modelParams = {"MEMC", "VT", "VR", "VP", "K", "A", "B", "C", "D", "FALLRATE", "USCALE" };
    String[] instanceParams = {"MEMC", "VT", "VR", "VP", "K", "A", "B", "C", "D", "FALLRATE", "USCALE" };
    String[] ivars = {"U"};

    public Neuron7XyceDevice()
    {
        name = "neuron";
        level = 7;
        defaultNodes = Arrays.asList(nodes);
        modelParameterNames = Arrays.asList(modelParams);
        internalVariables = Arrays.asList(ivars);
    }

    @Override
    public String getCapacitanceVar()
    {
        return "MEMC";
    }
}
