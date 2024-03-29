/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.util.Random;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.db.MNode;

/**
    Wraps an IteratorIndexed and steps through its entries in a random order.
    The order is determined only once, then remains constant through any number
    of cycles.
    The wrapper iterator is stored using member "inner". The wrapped iterator
    should not have any further sub-iterators under it.
**/
public class IteratorPermute extends IteratorIndexed
{
    protected int[] order;

    public IteratorPermute (IteratorIndexed child)
    {
        super (child.keyPath);  // We share state storage with child. Our keys will be prefixed with "permute".
        inner = child;
        count = child.count;
    }

    public boolean step ()
    {
        if (order == null)
        {
            order = new int[count];
            for (int i = 0; i < count; i++) order[i] = i;

            // permute
            Random r = Simulator.instance.get ().random;  // This simulator instance is not actually used for simulation. It just wraps the study's RNG.
            for (int i = 0; i < count - 1; i++)
            {
                int j = r.nextInt (count - i) + i;
                if (j != i)
                {
                    int temp = order[i];
                    order[i] = order[j];
                    order[j] = temp;
                }
            }
        }

        return super.step ();
    }

    public void assign (MNode model)
    {
        ((IteratorIndexed) inner).index = order[index];
        inner.assign (model);
    }

    public void save (MNode study)
    {
        inner.save (study);
        MNode n = node (study);
        n.set (index, "permuteIndex");

        if (order == null)
        {
            n.clear ("permuteOrder");
        }
        else
        {
            // TODO: for large counts, it may be better to save the random seed and regenerate.
            String orderString = String.valueOf (order[0]);
            for (int i = 1; i < count; i++) orderString += "," + order[i];
            n.set (orderString, "permuteOrder");
        }
    }

    public void load (MNode study)
    {
        inner.load (study);
        MNode n = node (study);
        index = n.getInt ("permuteIndex");

        String orderString = n.get ("permuteOrder");
        if (orderString.isBlank ())
        {
            order = null;
        }
        else
        {
            String pieces[] = orderString.split (",");
            order = new int[pieces.length];
            for (int i = 0; i < pieces.length; i++) order[i] = Integer.valueOf (pieces[i]);
        }
    }

    public boolean usesRandom ()
    {
        return true;
    }
}
