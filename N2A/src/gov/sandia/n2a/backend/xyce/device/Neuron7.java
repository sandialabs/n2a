/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class Neuron7 extends XyceDevice
{
    String[] nodes = {""};
    String[] modelParams = {"MEMC", "VT", "VR", "VP", "K", "A", "B", "C", "D", "FALLRATE", "USCALE" };
    String[] instanceParams = {"MEMC", "VT", "VR", "VP", "K", "A", "B", "C", "D", "FALLRATE", "USCALE" };
    String[] ivars = {"U"};

    public Neuron7()
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
