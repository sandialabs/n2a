/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
    The integrator for the Internal simulator.
    Internal is never meant to become a high-performance simulator, so Euler is all we ever expect to support.
    Space-efficiency is the true priority for Internal, since it supports code-generation for other backends,
    and thus makes the size of their models memory-bound.
**/
public class Simulator implements Iterable<Part>
{
    public Wrapper                     wrapper;  // reference to top-level model, which is also in the simulation queue
    public EventFactory                eventFactory;
    public Queue<Event>                eventQueue   = new PriorityQueue<Event> ();
    public List<ResizeRequest>         resizeQueue  = new LinkedList<ResizeRequest> ();
    public List<PopulationConnection>  connectQueue = new LinkedList<PopulationConnection> ();
    public TreeMap<Double,EventStep>   periods      = new TreeMap<Double,EventStep> ();
    public Random                      random;

    // Global shared data
    public Map<String,Matrix>        matrices = new HashMap<String,Matrix> ();
    public Map<String,Input .Holder> inputs   = new HashMap<String,Input .Holder> ();
    public Map<String,Output.Holder> outputs  = new HashMap<String,Output.Holder> ();
    public PrintStream               out;
    // Note: System.in will get bound into an Input.Holder if used at all.

    public static int BEFORE = -1;
    public static int DURING =  0;
    public static int AFTER  =  1;
    public int eventMode = DURING;

    public Event currentEvent;
    public boolean stop;  // Flag to terminate event loop as soon as possible

    public class ResizeRequest
    {
        public PopulationCompartment compartment;
        public int                   size;
        public ResizeRequest (PopulationCompartment compartment, int size)
        {
            this.compartment = compartment;
            this.size        = size;
        }
    }

    public Simulator (Wrapper wrapper, long seed)
    {
        this (wrapper, seed, new EventFactory ());
    }

    public Simulator (Wrapper wrapper, long seed, EventFactory factory)
    {
        try {out = new PrintStream (new File ("out").getAbsoluteFile ());}      // put in current working dir, which should be the job directory
        catch (FileNotFoundException e) {out = System.out;}  // if that fails, just use the default stdout

        random = new Random (seed);

        this.wrapper = wrapper;
        wrapper.simulator = this;

        eventFactory = factory;
        EventStep e = eventFactory.create (0.0, 1e-4);
        e.enqueue (wrapper);
        periods.put (e.dt, e);

        currentEvent = e;
        wrapper.init (this);
        updatePopulations ();

        if (e.head.next != e.head)  // if event is not empty (that is, if model did not move itself to a different period)
        {
            e.t = e.dt;
            eventQueue.add (e);
        }
    }

    public static Simulator getSimulator (Instance context)
    {
        Simulator simulator = null;
        if (context instanceof InstanceTemporaries)
        {
            simulator = ((InstanceTemporaries) context).simulator;
        }
        else
        {
            Instance top = context;
            while (top.container != null) top = top.container;
            if (top instanceof Wrapper) simulator = ((Wrapper) top).simulator;
        }
        return simulator;
    }

    public void run ()
    {
        // This is the core simulation loop.
        while (! eventQueue.isEmpty ()  &&  ! stop)
        {
            currentEvent = eventQueue.remove ();
            currentEvent.run (this);
        }
        // Simulation is done.

        // Close streams
        for (Entry<String,Input.Holder> h : inputs.entrySet ())
        {
            try {h.getValue ().stream.close ();}
            catch (IOException e) {}
        }
        for (Entry<String,Output.Holder> e : outputs.entrySet ())
        {
            Output.Holder h = e.getValue ();
            h.writeTrace ();
            h.out.close ();
        }
    }

    public void integrate (Instance i)
    {
        i.integrate (this);
    }

    public void updatePopulations ()
    {
        // Resize populations that have requested it
        for (ResizeRequest r : resizeQueue) r.compartment.resize (this, r.size);
        resizeQueue.clear ();

        // Evaluate connection populations that have requested it
        for (PopulationConnection p : connectQueue) p.connect (this);
        connectQueue.clear ();
    }

    public void move (Part i, double dt)
    {
        // find a matching event, or create one
        EventStep e = null;
        Entry<Double,EventStep> result = periods.floorEntry (dt);
        if (result != null) e = result.getValue ();
        if (e == null  ||  e.dt != dt)
        {
            e = eventFactory.create (currentEvent.t + dt, dt);
            periods.put (dt, e);
            eventQueue.add (e);
        }

        // transfer to new event's queue
        i.dequeue ();
        e.enqueue (i);
        //   keep top-level model and wrapper together in same event
        if (i.container.container == wrapper)  // This should always be safe, since only wrapper itself lacks a container.
        {
            wrapper.dequeue ();
            e.enqueue (wrapper);
        }
    }

    public double getNextDt ()
    {
        for (Event e : eventQueue)
        {
            if (e instanceof EventStep) return ((EventStep) e).dt;
        }
        return 1e-4;
    }

    public void resize (PopulationCompartment p, int n)
    {
        resizeQueue.add (new ResizeRequest (p, n));
    }

    public void connect (PopulationConnection p)
    {
        connectQueue.add (p);
    }

    public class PartIterator implements Iterator<Part>
    {
        Iterator<Event> eventIterator;
        EventStep       event;
        Part            part;

        public PartIterator ()
        {
            eventIterator = eventQueue.iterator ();
            nextEvent ();
        }

        public boolean hasNext ()
        {
            if (part == null) return false;
            return part != event.head;
        }

        public Part next ()
        {
            Part result = part;
            part = part.next;
            if (part == event.head) nextEvent ();
            return result;
        }

        public void nextEvent ()
        {
            event = null;
            while (eventIterator.hasNext ())
            {
                Event e = eventIterator.next ();
                if (e instanceof EventStep)
                {
                    event = (EventStep) e;
                    break;
                }
            }
            if (event == null) part = null;
            else               part = event.head.next;
        }

        public void remove ()
        {
            throw new UnsupportedOperationException ();
        }
    }

    public Iterator<Part> iterator ()
    {
        return new PartIterator ();
    }
}
