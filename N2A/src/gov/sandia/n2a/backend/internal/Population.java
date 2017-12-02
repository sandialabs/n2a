/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.LinkedList;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

/**
    An Instance which contains the global variables for a given kind of part,
    and which manages the group of instances as a whole.
**/
public class Population extends Instance
{
    // We create a null-terminated doubly-linked list. This is little more complicated
    // to manage than a fully-circular list, but it is worth the complexity to avoid
    // extra storage for the head object.
    public Part head; ///< List of all instances of this population. Mainly used for creating connections.
    public Part tail; ///< Last member in list. Used for reverse iteration.
    public Part old;  ///< First old part in list. All parts before this were added during current cycle. If old == null, then all parts are new.
    public int  n;  /// current number of live members
    public int  nextIndex;
    public LinkedList<Integer> availableIndex;

    protected Population (EquationSet equations, Part container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countGlobalFloat, bed.countGlobalObject);
    }

    /// @return The Population associated with the given position in EquationSet.connectionBindings collection
    public Population getTarget (int i)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        return ((Part) container).populations[bed.connectionTargets[i]];
    }

    public void init (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);
        resolve (temp.bed.globalReference);
        for (Variable v : temp.bed.globalInit)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        for (Variable v : temp.bed.globalBuffered)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
        // zero external buffered variables that may be written before first finish()
        for (Variable v : temp.bed.globalBufferedExternalWrite) set (v, v.type);  // v.type should be pre-loaded with zero-equivalent values

        if (equations.connectionBindings == null)
        {
            InternalBackendData bed = (InternalBackendData) equations.backendData;
            int requestedN = 1;
            if (bed.n.hasAttribute ("constant")) requestedN = (int) ((Scalar) bed.n.eval (this)).value;
            else                                 requestedN = (int) ((Scalar) get (bed.n)).value;
            resize (simulator, requestedN);
        }
        else
        {
            simulator.connect (this);  // queue to evaluate our connections
        }
    }

    public void integrate (Simulator simulator, double dt)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.globalIntegrated)
        {
            double a  = ((Scalar) get (v           )).value;
            double aa = ((Scalar) get (v.derivative)).value;
            setFinal (v, new Scalar (a + aa * dt));
        }
    }

    public void update (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
        for (Variable v : temp.bed.globalUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.readIndex != v.writeIndex) temp.set (v, temp.get (v));
            }
            else if (v.reference.variable.writeIndex >= 0)  // ensure this is not a "dummy" variable
            {
                if (v.assignment == Variable.REPLACE)
                {
                    temp.set (v, result);
                }
                else
                {
                    // the rest of these require knowing the current value of the working result, which is most likely external buffered
                    Type current = temp.getFinal (v.reference);
                    switch (v.assignment)
                    {
                        case Variable.ADD:      temp.set (v, current.add      (result)); break;
                        case Variable.MULTIPLY: temp.set (v, current.multiply (result)); break;
                        case Variable.DIVIDE:   temp.set (v, current.divide   (result)); break;
                        case Variable.MIN:      temp.set (v, current.min      (result)); break;
                        case Variable.MAX:      temp.set (v, current.max      (result)); break;
                    }
                }
            }
        }
        for (Variable v : temp.bed.globalBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        if (equations.connected) old = head;
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        // $n shares control with other specials, so coordinate them.
        // This work is split between here and after the finalize code below.
        if (bed.populationCanResize  &&  bed.populationCanGrowOrDie  &&  bed.n.derivative == null)
        {
            double oldN = ((Scalar) get      (bed.n)).value;
            double newN = ((Scalar) getFinal (bed.n)).value;
            if (newN != oldN) simulator.resize (this, (int) newN);  // $n was explicitly changed, so its value takes precedence
            else              simulator.resize (this, -1);  // -1 means to update $n from this.n. This can only be done after other parts are finalized, as they may impose structural dynamics via $p or $type.
        }

        for (Variable v : bed.globalBufferedExternal) setFinal (v, getFinal (v));
        for (Variable v : bed.globalBufferedExternalWrite)
        {
            switch (v.assignment)
            {
                case Variable.ADD:
                    set (v, v.type);  // initial value is zero-equivalent (additive identity)
                    break;
                case Variable.MULTIPLY:
                case Variable.DIVIDE:
                    // multiplicative identity
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).identity ());
                    else                          set (v, new Scalar (1));
                    break;
                case Variable.MIN:
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.POSITIVE_INFINITY));
                    else                          set (v, new Scalar (Double.POSITIVE_INFINITY));
                    break;
                case Variable.MAX:
                    if (v.type instanceof Matrix) set (v, ((Matrix) v.type).clear (Double.NEGATIVE_INFINITY));
                    else                          set (v, new Scalar (Double.NEGATIVE_INFINITY));
                    break;
                // For all other assignment types, do nothing. Effectively, buffered value is initialized to current value
            }
        }

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
            else  // $n is the only kind of structural dynamics, so only do a resize() when needed
            {
                if (requestedN != n) simulator.resize (this, requestedN);
            }
        }

        return true;
    }

    public void connect (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        Population A = getTarget (0);
        Population B = getTarget (1);
        if (A == null  ||  B == null) return;  // Nothing to connect. This should never happen, though we might have a unary connection.
        if (A.n == 0  ||  B.n == 0) return;
        if (A.old == A.head  &&  B.old == B.head) return;  // Only proceed if there are some new parts.

        // TODO: implement nearest-neighbor filtering

        int Amax = 0;
        int Amin = 0;
        int Bmax = 0;
        int Bmin = 0;
        if (bed.max[0] != null) Amax = (int) ((Scalar) get (bed.max[0])).value;
        if (bed.min[0] != null) Amin = (int) ((Scalar) get (bed.min[0])).value;
        if (bed.max[1] != null) Bmax = (int) ((Scalar) get (bed.max[1])).value;
        if (bed.min[1] != null) Bmin = (int) ((Scalar) get (bed.min[1])).value;

        Part c = new Part (equations, this);

        // Scan AxB
        Part Alast = A.old;
        Part Blast = B.head;
        boolean minSatisfied = false;
        while (! minSatisfied)
        {
            minSatisfied = true;

            // New A with all of B
            Part a = A.head;
            while (a != A.old)
            {
                c.setPart (0, a);
                int Acount = 0;
                if (Amax != 0  ||  Amin != 0) Acount = c.getCount (0);
                if (Amax != 0  &&  Acount >= Amax)  // early out: this part is already full, so skip
                {
                    a = a.after;
                    continue;
                }

                // iterate over B, with some shuffling each time
                Part Bnext = Blast.before;  // will change if we make some connections
                if (Bnext == null) Bnext = B.tail;
                Part b = Blast;
                do
                {
                    b = b.after;
                    if (b == null) b = B.head;

                    c.setPart (1, b);
                    if (Bmax != 0  &&  c.getCount (1) >= Bmax) continue;  // no room in this B
                    c.resolve ();
                    double create = c.getP (simulator);
                    if (create <= 0  ||  create < 1  &&  create < simulator.random.nextDouble ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    ((Part) container).event.enqueue (c);
                    c.init (simulator);
                    c = new Part (equations, this);
                    c.setPart (0, a);
                    Bnext = b;

                    if (Amax != 0)
                    {
                        if (++Acount >= Amax) break;  // stop scanning B once this A is full
                    }
                }
                while (b != Blast);
                Blast = Bnext;

                if (Amin != 0  &&  Acount < Amin) minSatisfied = false;
                a = a.after;
            }

            // New B with old A (new A x new B is already covered in case above)
            if (A.old != null)  // There exist some old A
            {
                Part b = B.head;
                while (b != B.old)
                {
                    c.setPart (1, b);
                    int Bcount = 0;
                    if (Bmax != 0  ||  Bmin != 0) Bcount = c.getCount (1);
                    if (Bmax != 0  &&  Bcount >= Bmax)
                    {
                        b = b.after;
                        continue;
                    }

                    // TODO: the projection from A to B could be inverted, and another spatial search structure built.
                    // For now, we don't use spatial constraints.

                    Part Anext;
                    if (Alast == A.old) Anext = A.tail;
                    else                Anext = Alast.before;
                    a = Alast;
                    do
                    {
                        a = a.after;
                        if (a == null) a = A.old;

                        c.setPart (0, a);
                        if (Amax != 0  &&  c.getCount (0) >= Amax) continue;
                        c.resolve ();
                        double create = c.getP (simulator);
                        if (create <= 0  ||  create < 1  &&  create < simulator.random.nextDouble ()) continue;
                        ((Part) container).event.enqueue (c);
                        c.init (simulator);
                        c = new Part (equations, this);
                        c.setPart (1, b);
                        Anext = a;

                        if (Bmax != 0)
                        {
                            if (++Bcount >= Bmax) break;
                        }
                    }
                    while (a != Alast);
                    Alast = Anext;

                    if (Bmin != 0  &&  Bcount < Bmin) minSatisfied = false;
                    b = b.after;
                }
            }

            // Check if minimums have been satisfied for old parts. New parts in both A and B were checked above.
            if (Amin != 0  &&  minSatisfied)
            {
                a = A.old;
                while (a != null)
                {
                    c.setPart (0, a);
                    if (c.getCount (0) < Amin)
                    {
                        minSatisfied = false;
                        break;
                    }
                    a = a.after;
                }
            }
            if (Bmin != 0  &&  minSatisfied)
            {
                Part b = B.old;
                while (b != null)
                {
                    c.setPart (1, b);
                    if (c.getCount (1) < Bmin)
                    {
                        minSatisfied = false;
                        break;
                    }
                    b = b.after;
                }
            }
        }
    }

    public void insert (Part p)
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

        if (equations.connected)
        {
            if (tail == null) tail = p;
            if (head != null) head.before = p;
            p.after  = head;
            p.before = null;
            head     = p;
        }
    }

    public void remove (Part p)
    {
        n--;  // presuming that p is actually here

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            if (availableIndex == null) availableIndex = new LinkedList<Integer> ();
            availableIndex.add ((int) ((Scalar) p.get (bed.index)).value);
        }

        if (equations.connected)
        {
            if (p == tail) tail = p.before;
            if (p == head) head = p.after;
            if (p == old ) old  = p.after;
            if (p.after  != null) p.after.before = p.before;
            if (p.before != null) p.before.after = p.after;
        }
    }

    public void resize (Simulator simulator, int requestedN)
    {
        if (requestedN < 0)  // indicates to update $n from actual part count
        {
            InternalBackendData bed = (InternalBackendData) equations.backendData;
            int currentN = (int) ((Scalar) get (bed.n)).value;
            // In general, $n can be fractional, which allows gradual growth over many cycles.
            // Only change $n if it does not truncate to same as actual n.
            if (currentN != n) setFinal (bed.n, new Scalar (n));
            return;
        }

        while (n < requestedN)
        {
            Part p = new Part (equations, this);
            insert (p);  // sets $index; increments n
            ((Part) container).event.enqueue (p);
            p.resolve ();
            p.init (simulator);
        }

        if (n > requestedN)
        {
            Part r = tail;  // reverse iterator
            while (n > requestedN)
            {
                if (r == null) throw new EvaluationException ("Internal inconsistency in population count.");
                Part p = r;
                r = r.before;
                p.die ();  // Part.die() is responsible to call remove(). p itself won't dequeue until next simulator cycle.
            }
        }
    }
}
