/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuron;

import gov.sandia.n2a.backend.neuroml.BackendNeuroML;
import gov.sandia.n2a.db.MNode;

public class BackendNeuron extends BackendNeuroML
{
    @Override
    public String getName ()
    {
        return "NEURON";
    }

    @Override
    public void start (MNode job)
    {
        Thread simulationThread = new SimulationThread (job);
        simulationThread.setDaemon (true);
        simulationThread.start ();
    }

    public class SimulationThread extends BackendNeuroML.SimulationThread
    {
        public SimulationThread (MNode job)
        {
            super (job);
            simulator = "neuron";  // Override the simulator string from backend. This is all it takes to switch to NEURON.
        }
    }
}
