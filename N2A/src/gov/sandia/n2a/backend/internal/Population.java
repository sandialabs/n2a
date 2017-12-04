/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.Iterator;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
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
    public int n;  // current number of live members

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

        if (temp.bed.index != null)
        {
            valuesFloat[temp.bed.indexNext] = 0;  // Using floats directly as index counter limits us to 24 bits, or about 16 million. Internal is not intended for large simulations, so this limitation is acceptable.
            // indexAvailable is initially null
        }

        // TODO: A self-connection will have to do both resize() and connect().
        // It's just coincidental that these are mutually exclusive in the current code.
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

    @SuppressWarnings("unchecked")
    public void connect (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        Population A = getTarget (0);
        Population B = getTarget (1);
        if (A == null  ||  B == null) return;  // Nothing to connect. This should never happen, though we might have a unary connection.
        if (A.n == 0  ||  B.n == 0) return;

        InternalBackendData Abed = (InternalBackendData) A.equations.backendData;
        InternalBackendData Bbed = (InternalBackendData) B.equations.backendData;
        ArrayList<Part> Ainstances = (ArrayList<Part>) A.valuesObject[Abed.instances];
        ArrayList<Part> Binstances = (ArrayList<Part>) B.valuesObject[Bbed.instances];
        if (Ainstances == null  ||  Binstances == null) return;

        int Afirstborn = (int) A.valuesFloat[Abed.firstborn];
        int Bfirstborn = (int) B.valuesFloat[Bbed.firstborn];
        if (Afirstborn >= Ainstances.size ()  &&  Bfirstborn >= Binstances.size ()) return;  // Only proceed if there are some new parts.
        simulator.clearNew (A);
        simulator.clearNew (B);

        int Asize = Ainstances.size ();
        int Bsize = Binstances.size ();

        // TODO: implement nearest-neighbor filtering

        int Amax = 0;
        int Bmax = 0;
        if (bed.max[0] != null) Amax = (int) ((Scalar) get (bed.max[0])).value;
        if (bed.max[1] != null) Bmax = (int) ((Scalar) get (bed.max[1])).value;
        // TODO: implement $min, or consider eliminating it from the language
        // $max is easy, but $min requires one or more forms of expensive accounting to do correctly.
        // Problems include:
        // 1) need to prevent duplicate connections
        // 2) should pick the highest probability connections
        // A list of connections held by each target could solve #1.
        // Such an approach may be necessary for ongoing maintenance of connections, beyond just this new-connection process.
        // A temporary list of connections that were rejected, sorted by probability, could solve issue #2.
        // However, this is more difficult to implement for any but the outer loop. Could implement an
        // outer loop for each of the other populations, just for fulfilling $min.

        // Scan AxB
        Part c = new Part (equations, this);
        for (int i = 0; i < Asize; i++)
        {
            Part a = Ainstances.get (i);
            if (a == null) continue;
            boolean Aold = a.valuesFloat[Abed.newborn] == 0;

            c.setPart (0, a);
            int Acount = 0;
            if (Amax != 0)
            {
                Acount = c.getCount (0);
                if (Acount >= Amax) continue;  // early out: this part is already full, so skip
            }

            // iterate over B, with some shuffling each time
            int count;
            int offset;
            if (Aold)  // focus on portion of B more likely to contain new parts
            {
                count  = Bsize - Bfirstborn;
                offset = Bfirstborn;
            }
            else   // a is new, so process all of B
            {
                count  = Bsize;
                offset = 0;
            }
            int j    = (int) Math.round (Math.random () * (count - 1));
            int stop = j + count;
            for (; j < stop; j++)
            {
                Part b = Binstances.get (j % count + offset);
                if (b == null) continue;
                boolean Bold = b.valuesFloat[Bbed.newborn] == 0;
                if (Aold  &&  Bold) continue;  // Both parts are old, so don't process

                c.setPart (1, b);
                if (Bmax != 0  &&  c.getCount (1) >= Bmax) continue;  // no room in this B

                c.resolve ();
                double create = c.getP (simulator);
                if (create <= 0  ||  create < 1  &&  create < simulator.random.nextDouble ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                ((Part) container).event.enqueue (c);
                c.init (simulator);
                c = new Part (equations, this);
                c.setPart (0, a);

                if (Amax != 0)
                {
                    if (++Acount >= Amax) break;  // stop scanning B once this A is full
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void clearNew ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
        int count     = instances.size ();
        int firstborn = (int) valuesFloat[bed.firstborn];
        for (int i = firstborn; i < count; i++)
        {
            Part p = instances.get (i);
            if (p == null) continue;
            p.valuesFloat[bed.newborn] = 0;
        }
        valuesFloat[bed.firstborn] = count;
    }

    @SuppressWarnings("unchecked")
    public void insert (Part p)
    {
        n++;

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            int index;
            if (valuesObject[bed.indexAvailable] == null)
            {
                index = (int) valuesFloat[bed.indexNext]++;
            }
            else
            {
                ArrayList<Integer> availableIndex = (ArrayList<Integer>) valuesObject[bed.indexAvailable];
                index = availableIndex.remove (availableIndex.size () - 1);
                if (availableIndex.size () < 1) valuesObject[bed.indexAvailable] = null;
            }
            p.set (bed.index, new Scalar (index));

            if (bed.instances >= 0)
            {
                ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
                if (instances == null)
                {
                    instances = new ArrayList<Part> (index + 1);
                    valuesObject[bed.instances] = instances;
                }
                for (int size = instances.size (); size <= index; size++) instances.add (null);
                instances.set (index, p);

                if (equations.connected)
                {
                    p.valuesFloat[bed.newborn] = 1;
                    valuesFloat[bed.firstborn] = Math.min (valuesFloat[bed.firstborn], index);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void remove (Part p)
    {
        n--;  // presuming that p is actually here

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.index != null)
        {
            int index = (int) ((Scalar) p.get (bed.index)).value;

            ArrayList<Integer> availableIndex = (ArrayList<Integer>) valuesObject[bed.indexAvailable];
            if (availableIndex == null)
            {
                availableIndex = new ArrayList<Integer> ();
                valuesObject[bed.indexAvailable] = availableIndex;
            }
            availableIndex.add (index);

            if (bed.instances >= 0)
            {
                ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
                instances.set (index, null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void resize (Simulator simulator, int requestedN)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        if (requestedN < 0)  // indicates to update $n from actual part count
        {
            int currentN = (int) ((Scalar) get (bed.n)).value;
            // In general, $n can be fractional, which allows gradual growth over many cycles.
            // Only change $n if it does not truncate to same as actual n.
            if (currentN != n) setFinal (bed.n, new Scalar (n));
            return;
        }

        while (n < requestedN)
        {
            Part p = new Part (equations, this);
            ((Part) container).event.enqueue (p);
            p.resolve ();
            p.init (simulator);
        }

        if (n > requestedN)
        {
            ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
            for (int i = instances.size () - 1; i >= 0  &&  n > requestedN; i--)
            {
                Part p = instances.get (i);
                if (p == null) continue;
                p.die ();  // Part.die() is responsible to call remove(), which decreases n. p itself won't dequeue until next simulator cycle.
            }
        }
    }
}
