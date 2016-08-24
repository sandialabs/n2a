/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import java.util.LinkedList;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.type.Scalar;

public class PopulationCompartment extends Population
{
    // We create a null-terminated doubly-linked list. This is little more complicated
    // to manage than a fully-circular list, but it is worth the complexity to avoid
    // extra storage for the head object.
    public Compartment head; ///< List of all instances of this population. Mainly used for creating connections.
    public Compartment tail; ///< Last member in list. Used for reverse iteration.
    public Compartment old;  ///< First old part in list. All parts before this were added during current cycle. If old == null, then all parts are new.
    public int n;  /// current number of live members
    public int nextIndex;
    public LinkedList<Integer> availableIndex;

    public PopulationCompartment (EquationSet equations, Part container)
    {
        super (equations, container);
    }

    public void insert (Compartment p)
    {
        n++;

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            int index;
            if (availableIndex == null)
            {
                index = nextIndex++;
            }
            else
            {
                index = availableIndex.remove ();
                if (availableIndex.size () < 1) availableIndex = null;
            }
            p.set (bed.index, new Scalar (index));
        }

        if (tail == null) tail = p;
        if (head != null) head.before = p;
        p.after  = head;
        p.before = null;
        head     = p;
    }

    public void remove (Compartment p)
    {
        n--;  // presuming that p is actually here

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            if (availableIndex == null) availableIndex = new LinkedList<Integer> ();
            availableIndex.add ((int) ((Scalar) p.get (bed.index)).value);
        }

        if (p == tail) tail = p.before;
        if (p == head) head = p.after;
        if (p == old ) old  = p.after;
        if (p.after  != null) p.after.before = p.before;
        if (p.before != null) p.before.after = p.after;
    }

    public void init (Simulator simulator)
    {
        super.init (simulator);

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int requestedN = 1;
        if (bed.n.hasAttribute ("constant")) requestedN = (int) ((Scalar) bed.n.eval (this)).value;
        else                                 requestedN = (int) ((Scalar) get (bed.n)).value;
        resize (simulator, requestedN);
    }

    public void update (Simulator simulator)
    {
        super.update (simulator);
        old = head;
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.populationCanResize  &&  bed.populationCanGrowOrDie  &&  bed.n.derivative == null)  // $n shares control with other specials, so must coordinate them
        {
            double oldN = ((Scalar) get      (bed.n)).value;
            double newN = ((Scalar) getFinal (bed.n)).value;
            if (newN != oldN) simulator.resize (this, (int) newN);  // $n was explicitly changed, so its value takes precedence
            else              simulator.resize (this, -1);  // -1 means to update $n from n. This can only be done after other parts are finalized, as they may impose structural dynamics via $p or $type.
        }

        boolean result = super.finish (simulator);

        if (bed.populationCanResize)
        {
            int requestedN = (int) ((Scalar) get (bed.n)).value;  // This is the finalized value of $n.
            if (bed.populationCanGrowOrDie)
            {
                if (bed.n.derivative != null)  // $n' exists
                {
                    // the rate of change in $n is pre-determined, so it relentlessly overrides any other structural dynamics
                    simulator.resize (this, requestedN);
                }
            }
            else  // $n is the only kind of structural dynamics, so simply do a resize() when needed
            {
                if (requestedN != n) simulator.resize (this, requestedN);
            }
        }

        return result;
    }

    public void resize (Simulator simulator, int requestedN)
    {
        if (requestedN < 0)  // indicated to update $n from actual part count
        {
            InternalBackendData bed = (InternalBackendData) equations.backendData;
            int currentN = (int) ((Scalar) get (bed.n)).value;
            if (currentN != n)  // conditional so we can preserve fractional $n unless it is very wrong
            {
                setFinal (bed.n, new Scalar (n));
            }
            return;
        }

        while (n < requestedN)
        {
            Compartment p = new Compartment (equations, this);
            insert (p);  // sets $index; increments n
            container.enqueue (p);
            p.resolve ();
            p.init (simulator);
        }

        if (n > requestedN)
        {
            Compartment r = tail;  // reverse iterator
            while (n > requestedN)
            {
                if (r == null) throw new EvaluationException ("Internal inconsistency in population count.");
                Compartment p = r;
                r = r.before;
                p.die ();  // Part.die() is responsible to call remove(). p itself won't dequeue until next simulator cycle.
            }
        }
    }
}
