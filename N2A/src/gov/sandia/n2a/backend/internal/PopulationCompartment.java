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
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class PopulationCompartment extends Population
{
    LinkedList<Part> membersNew;
    LinkedList<Part> membersOld;
    int n;  /// current number of live members
    int nextIndex;
    LinkedList<Integer> availableIndex;

    public PopulationCompartment (EquationSet equations, Instance container)
    {
        super (equations, container);
    }

    public void add (Part p)
    {
        n++;

        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;

        if (availableIndex == null) availableIndex = new LinkedList<Integer> ();
        int index;
        if (availableIndex.size () > 0) index = availableIndex.remove ();
        else                            index = nextIndex++;
        p.set (bed.index, new Scalar (index));

        if (membersNew == null) membersNew = new LinkedList<Part> ();
        membersNew.add (p);
    }

    public void remove (Part p)
    {
        n--;  // presuming that p is actually here

        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;

        if (availableIndex == null) availableIndex = new LinkedList<Integer> ();
        availableIndex.add ((int) ((Scalar) p.get (bed.index)).value);

        if (membersOld != null  &&  membersOld.remove (p)) return;
        if (membersNew != null)     membersNew.remove (p);
    }

    public void init (Euler simulator)
    {
        super.init (simulator);

        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;
        int requestedN = 1;
        if (bed.n.hasAttribute ("constant")) requestedN = (int) ((Scalar) bed.n.eval (this)).value;
        else                                 requestedN = (int) ((Scalar) get (bed.n)).value;
        resize (simulator, requestedN);
    }

    public void prepare ()
    {
        super.prepare ();

        if (membersNew != null  &&  membersNew.size () > 0)
        {
            if (membersOld == null) membersOld = new LinkedList<Part> ();
            membersOld.addAll (membersNew);
            membersNew.clear ();
        }

        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;
        if (bed.countChangesWithoutN)
        {
            int requestedN = (int) ((Scalar) get (bed.n)).value;
            if (requestedN != n) set (bed.n, new Scalar (n));  // conditional so we can preserve fractional requested $n unless it is very wrong
        }
    }

    public void update (Euler simulator)
    {
        super.update (simulator);

        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;
        if (! bed.n.hasAny (new String[] {"constant", "initOnly", "externalWrite", "externalRead"}))  // This case should be mutually exclusive with the one in Population.finish().
        {
            int requestedN = (int) ((Scalar) get (bed.n)).value;
            if (requestedN != n) simulator.resize (this, requestedN);
        }
    }

    public boolean finish (Euler simulator)
    {
        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;
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
        InternalSimulation.BackendData bed = (InternalSimulation.BackendData) equations.backendData;

        while (n < requestedN)
        {
            Part p = new Part (equations, container);
            add (p);  // sets $index; increments n
            simulator.enqueue (p);
            p.init (simulator);
        }

        while (n > requestedN)
        {
            if (membersOld.size () > 0)
            {
                Part p = membersOld.getFirst ();
                if (((Scalar) p.get (bed.live)).value != 0) p.die ();  // Part.die() is responsible to call remove(). p itself won't dequeue until next simulator cycle.
            }
            else if (membersNew.size () > 0)
            {
                Part p = membersNew.getFirst ();
                if (((Scalar) p.get (bed.live)).value != 0) p.die ();
            }
            else throw new EvaluationException ("Inconsistent $n");
        }
    }
}
