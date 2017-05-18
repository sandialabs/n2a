/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class Neuron1 extends XyceDevice
{
    String[] nodes = {"", "0"};
    String[] modelParams = {"CMEM", "GMEM", "ELEAK", "VREST", "GNA", "ENA", "GK", "EK" };
    String[] instanceParams = {};
    String[] ivars = {"N", "M", "H"};

    public Neuron1()
    {
        name = "neuron";
        level = 1;
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
