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
    // extra storage for the head.
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

    public void init (Euler simulator)
    {
        super.init (simulator);

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int requestedN = 1;
        if (bed.n.hasAttribute ("constant")) requestedN = (int) ((Scalar) bed.n.eval (this)).value;
        else                                 requestedN = (int) ((Scalar) get (bed.n)).value;
        resize (simulator, requestedN);
    }

    public void prepare ()
    {
        super.prepare ();

        old = head;

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.populationChangesWithoutN)  // This flag will not be set if $n is not stored. (It would be better to force $n to be stored if this flag should be set, but the will require more tricky pre-processing.)
        {
            int requestedN = (int) ((Scalar) get (bed.n)).value;
            if (requestedN != n) set (bed.n, new Scalar (n));  // conditional so we can preserve fractional requested $n unless it is very wrong
        }
    }

    public void update (Euler simulator)
    {
        super.update (simulator);

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (! bed.n.hasAny (new String[] {"constant", "initOnly", "externalWrite", "externalRead"}))  // This case should be mutually exclusive with the one in Population.finish().
        {
            int requestedN = (int) ((Scalar) get (bed.n)).value;
            if (requestedN != n) simulator.resize (this, requestedN);
        }
    }

    public boolean finish (Euler simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.globalBufferedExternal.contains (bed.n))
        {
            double oldN = ((Scalar) get      (bed.n)).value;
            double newN = ((Scalar) getFinal (bed.n)).value;
            if (newN != oldN) simulator.resize (this, (int) newN);
        }
        return super.finish (simulator);
    }

    public void resize (Euler simulator, int requestedN)
    {
        while (n < requestedN)
        {
            Compartment p = new Compartment (equations, this);
            insert (p);  // sets $index; increments n
            simulator.enqueue (p);
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
