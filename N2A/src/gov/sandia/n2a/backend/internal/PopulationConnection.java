/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.type.Scalar;

public class PopulationConnection extends Population
{
    public PopulationConnection (EquationSet equations, Part container)
    {
        super (equations, container);
    }

    /// @return The Population associated with the given position in EquationSet.connectionBindings collection
    public Population getTarget (int i)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        return ((Part) container).populations[bed.connectionTargets[i]];
    }

    public void init (Simulator simulator)
    {
        super.init (simulator);
        simulator.connect (this);  // queue to evaluate our connections
    }

    public void connect (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        PopulationCompartment A = (PopulationCompartment) getTarget (0);
        PopulationCompartment B = (PopulationCompartment) getTarget (1);
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

        Connection c = new Connection (equations, this);

        // Scan AxB
        Compartment Alast = A.old;
        Compartment Blast = B.head;
        boolean minSatisfied = false;
        while (! minSatisfied)
        {
            minSatisfied = true;

            // New A with all of B
            Compartment a = A.head;
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
                Compartment Bnext = Blast.before;  // will change if we make some connections
                if (Bnext == null) Bnext = B.tail;
                Compartment b = Blast;
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
                    c = new Connection (equations, this);
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
                Compartment b = B.head;
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

                    Compartment Anext;
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
                        c = new Connection (equations, this);
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
                Compartment b = B.old;
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
}
