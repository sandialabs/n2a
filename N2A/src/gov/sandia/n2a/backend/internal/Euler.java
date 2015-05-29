/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.type.Instance;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
    The integrator for the Internal simulator.
    Internal is never meant to become a high-performance simulator, so Euler is all we ever expect to support.
    Space-efficiency is the true priority for Internal, since it supports code-generation for other backends,
    and thus makes the size of their models memory-bound.
**/
public class Euler
{
    public Wrapper wrapper;  // holds top-level model
    public double t  = 0;
    public double dt = 1e-4;
    public List<Instance>                     queue        = new LinkedList<Instance> ();
    public Map<PopulationCompartment,Integer> resizeQueue  = new TreeMap<PopulationCompartment,Integer> ();
    public List<PopulationConnection>         connectQueue = new LinkedList<PopulationConnection> ();
    public Random uniform = new Random ();

    public void run ()
    {
        t = 0;  // updated in middle of loop below, just before integration
        while (! queue.isEmpty ())
        {
            // Evaluate connection populations that have requested it
            for (PopulationConnection p : connectQueue) p.connect (this);
            connectQueue.clear ();

            // Update parts
            t += dt;
            integrate ();
            for (Instance i : queue) i.prepare ();
            for (Instance i : queue) i.update (this);
            ListIterator<Instance> it = queue.listIterator ();
            while (it.hasNext ())
            {
                Instance i = it.next ();
                if (! i.finish (this)) it.remove ();  // finish() returns false if this instance should be removed from simulation
            }

            // Resize populations that have requested it
            for (Entry<PopulationCompartment,Integer> r : resizeQueue.entrySet ())
            {
                r.getKey ().resize (this, r.getValue ().intValue ());
            }
            resizeQueue.clear ();
        }

        wrapper.writeHeaders ();
    }

    /**
        Do all processing that occurs while $t==0.
        When a model completes its init() function, all its sub-parts get recursively added to the simulator queue.
        The only thing left to do is make the connections, which occurs in the main loop above just before $t is incremented.
        This function exists specifically to support other backends that use Internal to prepare network structures. 
        This should not be called if run() will be called.
     */
    public void finishInitCycle ()
    {
        for (PopulationConnection p : connectQueue) p.connect (this);
        connectQueue.clear ();
    }

    public void integrate ()
    {
        for (Instance i : queue) i.integrate (this);
    }

    public void move (double value)
    {
        dt = value;
    }

    public void enqueue (Instance i)
    {
        queue.add (i);
    }

    public void resize (PopulationCompartment p, int n)
    {
        resizeQueue.put (p, new Integer (n));
    }

    public void connect (PopulationConnection p)
    {
        connectQueue.add (p);
    }

    public void debugQueue ()
    {
        ListIterator<Instance> it = queue.listIterator ();
        while (it.hasNext ())
        {
            Instance i = it.next ();
            System.out.println (i.getClass ().getSimpleName ());
        }
    }
}
