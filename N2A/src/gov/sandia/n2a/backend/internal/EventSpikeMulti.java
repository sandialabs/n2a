package gov.sandia.n2a.backend.internal;

import java.util.Iterator;
import java.util.List;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.type.Instance;

public class EventSpikeMulti extends EventSpike
{
    public List<Instance> targets;

    public void run (Simulator simulator)
    {
        setFlag ();
        for (Instance i : targets) simulator.integrate (i);
        for (Instance i : targets) i.update (simulator);
        for (Instance i : targets)
        {
            boolean live = i.finish (simulator);
            InternalBackendData bed = (InternalBackendData) i.equations.backendData;
            for (Variable v : bed.eventReferences) ((Instance) i.valuesObject[v.reference.index]).finishEvent (v.reference.variable);
            if (! live) i.dequeue ();
        }
    }

    public void setFlag ()
    {
        Iterator<Instance> it = targets.iterator ();
        while (it.hasNext ())
        {
            Instance i = it.next ();
            if (i == null) it.remove ();
            else eventType.setLatch (i);
        }
    }
}
