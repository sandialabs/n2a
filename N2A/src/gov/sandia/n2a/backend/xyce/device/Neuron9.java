/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class Neuron9 extends XyceDevice
{
    String[] nodes = {"", "0"};
    String[] modelParams = {"CMEM", "GMEM", "ELEAK", "VREST", "GNA", "ENA", "GK", "EK" };
    String[] instanceParams = {};
    String[] ivars = {"N", "M", "H"};

    public Neuron9()
    {
        name = "neuron";
        level = 9;
        defaultNodes = Arrays.asList(nodes);
        modelParameterNames = Arrays.asList(modelParams);
        internalVariables = Arrays.asList(ivars);
    }

    @Override
    public String getCapacitanceVar()
    {
        return "CMEM";
    }
}
