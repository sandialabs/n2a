package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.type.Instance;

public class EventSpikeSingle extends EventSpike
{
    Instance target;

    public void run (Simulator simulator)
    {
        setFlag ();
        simulator.integrate (target);
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
