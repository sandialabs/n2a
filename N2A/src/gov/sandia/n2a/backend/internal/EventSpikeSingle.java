/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.type.Instance;

public class EventSpikeSingle extends EventSpike
{
    Instance target;

    public void run (Simulator simulator)
    {
        setFlag ();
        target.integrate (simulator);
        target.update (simulator);
        boolean live = target.finish (simulator);
        InternalBackendData bed = (InternalBackendData) target.equations.backendData;
        for (Variable v : bed.eventReferences) ((Instance) target.valuesObject[v.reference.index]).finishEvent (v.reference.variable);
        if (! live) target.dequeue ();
    }

    public void setFlag ()
    {
        eventType.setLatch (target);
    }
}
