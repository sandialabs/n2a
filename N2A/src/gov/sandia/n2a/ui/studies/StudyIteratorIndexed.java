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
public abstract class StudyIteratorIndexed extends StudyIterator
{
    protected int index = -1;
    protected int count;  // Must be set by concrete class constructor.

    public StudyIteratorIndexed (String[] keys)
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
