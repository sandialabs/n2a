/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

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
    public Scalar t  = new Scalar (0);
    public Scalar dt = new Scalar (1e-4);
    public List<Instance>                     queue        = new LinkedList<Instance> ();
    public Map<PopulationCompartment,Integer> resizeQueue  = new TreeMap<PopulationCompartment,Integer> ();
    public List<PopulationConnection>         connectQueue = new LinkedList<PopulationConnection> ();
    public Random uniform = new Random ();

    public void run ()
    {
        t.value = 0;  // updated in middle of loop below, just before integration
        while (! queue.isEmpty ())
        {
            // Evaluate connection populations that have requested it
            for (PopulationConnection p : connectQueue) p.connect (this);
            connectQueue.clear ();

            // Update parts
            t.value += dt.value;
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

    public void integrate ()
    {
        for (Instance i : queue) i.integrate (this);
    }

    public void move (double value)
    {
        dt.value = value;
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
}
