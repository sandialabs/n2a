package gov.sandia.n2a.backend.internal;

public class EventStep extends Event
{
    public double dt = 1e-4;  // Duration of one simulation step.
    public Part head = new Part ();  // doubly-linked list

    public EventStep ()
    {
        head.next     = head;
        head.previous = head;
    }

    public void run (Simulator simulator)
    {
        Part i = head.next;
        while (i != head)
        {
            simulator.integrate (i);
            i = i.next;
        }

        i = head.next;
        while (i != head)
        {
            i.update (simulator);
            i = i.next;
        }

        i = head.next;
        while (i != head)
        {
            if (! i.finish (simulator)) dequeue (i);  // finish() returns false if the instance should be removed from simulation
            i = i.next;  // dequeue() does not change i's own pointers, so this is safe
        }

        simulator.updatePopulations ();

        if (head.next == head)  // our list of instances is empty, so die
        {
            simulator.periods.remove (dt);
        }
        else  // still have instances, so re-queue event
        {
            t += dt;
            simulator.eventQueue.add (this);
        }
    }

    public void debugQueue ()
    {
        Part i = head.next;
        while (i != head)
        {
            System.out.println (i.getClass ().getSimpleName ());
            i = i.next;
        }
    }

    public void enqueue (Part p)
    {
        p.event         = this;
        p.next          = head.next;
        p.previous      = head;
        p.next.previous = p;
        p.previous.next = p;
    }

    public void dequeue (Part p)
    {
        p.previous.next = p.next;
        p.next.previous = p.previous;
    }
}
