/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.MNode;

/**
    An iterator that steps through a discrete set of items.
**/
public class IteratorIndexed extends StudyIterator
{
    protected int index = -1;
    protected int count;  // Must be set by concrete class constructor.

    public IteratorIndexed (String[] keys)
    {
        super (keys);
    }

    public int count ()
    {
        int result = count;
        if (inner != null) result *= inner.count ();
        return result;
    }

    public void restart ()
    {
        index = 0;
    }

    public boolean step ()
    {
        index++;
        return index < count;
    }

    @Override
    public void assign (MNode model)
    {
        // Do nothing. We simply repeat the run "count" times with an unmodified value.
        // If this is set on $metadata.seed, then we get Monte-Carlo runs.
        // The seed is provided by the backend, which means it is not repeatable
        // between studies. To get repeatable MC sampling, specify a random
        // generator, eg: $metadata.seed.study=uniform(100000)
    }

    public void save (MNode study)
    {
        if (inner != null) inner.save (study);
        MNode n = node (study);
        n.set (index, "index");
    }

    public void load (MNode study)
    {
        if (inner != null) inner.load (study);
        MNode n = node (study);
        index = n.getInt ("index");
    }
}
