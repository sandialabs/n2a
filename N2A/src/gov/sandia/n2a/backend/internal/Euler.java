/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.type.Instance;
import gov.sandia.umf.platform.UMF;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
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
public class Euler implements Iterable<Instance>
{
    public Wrapper                     wrapper;  // reference to top-level model, which is also in the simulation queue
    public Queue<Event>                eventQueue   = new PriorityQueue<Event> ();
    public List<ResizeRequest>         resizeQueue  = new LinkedList<ResizeRequest> ();
    public List<PopulationConnection>  connectQueue = new LinkedList<PopulationConnection> ();
    public TreeMap<Double,EventStep>   periods      = new TreeMap<Double,EventStep> ();
    public Random                      uniform      = new Random ();

    // trace
    public Map<String,Integer> columnMap    = new HashMap<String,Integer> ();  ///< For trace(). Maps from column name to column position.
    public List<Float>         columnValues = new ArrayList<Float> ();         ///< For trace(). Holds current value for each column.
    public PrintStream         out          = System.out;
    public PrintStream         err          = System.err;

    public Event currentEvent;

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

    public Euler (Wrapper wrapper, String jobDir)
    {
        if (jobDir.isEmpty ())
        {
            // Fall back: make paths relative to n2a data directory
            System.setProperty ("user.dir", UMF.getAppResourceDir ().getAbsolutePath ());
        }
        else
        {
            // Make paths relative to job directory
            System.setProperty ("user.dir", new File (jobDir).getAbsolutePath ());

            // Setup out and err streams first, so init phase can log output properly.
            try
            {
                out = new PrintStream (new File (jobDir, "out"));
                err = new PrintStream (new File (jobDir, "err"));
            }
            catch (FileNotFoundException e)
            {
                // out and err should retain their defaults (System.out and System.err) if an exception occurs
            }
        }

        this.wrapper = wrapper;
        wrapper.simulator = this;

        EventStep e = new EventStep ();
        e.head.enqueue (wrapper);
        periods.put (e.dt, e);

        currentEvent = e;
        wrapper.init (this);
        for (PopulationConnection p : connectQueue) p.connect (this);
        connectQueue.clear ();

        if (e.head.next != e.head)  // if event is not empty (that is, if model did not move itself to a different period)
        {
            e.t = e.dt;
            eventQueue.add (e);
        }
    }

    public static Euler getSimulator (Instance context)
    {
        Euler simulator = null;
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
        while (! eventQueue.isEmpty ())
        {
            currentEvent = eventQueue.remove ();
            currentEvent.run (this);
        }

        writeHeaders ();
    }

    public void integrate (Instance i)
    {
        i.integrate (this);
    }

    public void updatePopulations ()
    {
        // Resize populations that have requested it
        for (ResizeRequest r : resizeQueue)
        {
            r.compartment.resize (this, r.size);
        }
        resizeQueue.clear ();

        // Evaluate connection populations that have requested it
        for (PopulationConnection p : connectQueue) p.connect (this);
        connectQueue.clear ();
    }

    public void move (Instance i, double dt)
    {
        // find a matching event, or create one
        EventStep e = null;
        Entry<Double,EventStep> result = periods.floorEntry (dt);
        if (result != null) e = result.getValue ();
        if (e == null  ||  e.dt != dt)
        {
            e = new EventStep ();
            e.dt = dt;
            e.t = currentEvent.t + dt;
            periods.put (dt, e);
            eventQueue.add (e);
        }

        // transfer to new event's queue
        i.dequeue ();
        e.head.enqueue (i);
        //   keep top-level model and wrapper together in same event
        if (i.container.container == wrapper)  // This should always be safe, since only wrapper itself lacks a container.
        {
            wrapper.dequeue ();
            e.head.enqueue (wrapper);
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

    public void trace (String column, float value)
    {
        Integer index = columnMap.get (column);
        if (index == null)
        {
            columnMap.put (column, columnValues.size ());
            columnValues.add (value);
        }
        else
        {
            columnValues.set (index, value);
        }
    }

    public void writeTrace ()
    {
        int last = columnValues.size () - 1;
        for (int i = 0; i <= last; i++)
        {
            Float c = columnValues.get (i);
            if (! c.isNaN ()) out.print (c);
            if (i < last) out.print ("\t");
            columnValues.set (i, Float.NaN);
        }
        if (last >= 0) out.println ();
    }

    public void writeHeaders ()
    {
        int count = columnMap.size ();
        int last = count - 1;
        String headers[] = new String[count];
        for (Entry<String,Integer> i : columnMap.entrySet ())
        {
            headers[i.getValue ()] = i.getKey ();
        }
        for (int i = 0; i < count; i++)
        {
            out.print (headers[i]);
            if (i < last) out.print ("\t");
        }
        if (last >= 0) out.println ();
    }

    public class InstanceIterator implements Iterator<Instance>
    {
        Iterator<Event> eventIterator;
        EventStep       event;
        Instance        instance;

        public InstanceIterator ()
        {
            eventIterator = eventQueue.iterator ();
            nextEvent ();
        }

        public boolean hasNext ()
        {
            if (instance == null) return false;
            return instance != event.head;
        }

        public Instance next ()
        {
            Instance result = instance;
            instance = instance.next;
            if (instance == event.head) nextEvent ();
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
            if (event == null) instance = null;
            else               instance = event.head.next;
        }

        public void remove ()
        {
            throw new UnsupportedOperationException ();
        }
    }

    public Iterator<Instance> iterator ()
    {
        return new InstanceIterator ();
    }
}
