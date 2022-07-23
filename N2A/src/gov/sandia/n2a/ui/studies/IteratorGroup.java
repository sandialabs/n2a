/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;

/**
    Contains a set of IteratorIndexed which step together, rather than combinatorially.
    Child iterators can either be permuted or straight. If permuted, then we have a
    latin hypercube.
**/
public class IteratorGroup extends IteratorIndexed
{
    protected List<IteratorIndexed> children = new ArrayList<IteratorIndexed> ();

    /**
        @param keys Shared with one of the children iterators. Group state will be
        saved with name prefix "group".
    **/
    public IteratorGroup (String[] keys)
    {
        super (keys);
    }

    public void add (IteratorIndexed child)
    {
        children.add (child);
        if (child.count > count) count = child.count;
    }

    public void restart ()
    {
        super.restart ();
        for (IteratorIndexed child : children) child.restart ();
    }

    public boolean step ()
    {
        for (IteratorIndexed child : children)
        {
            if (! child.step ()) child.restart ();
        }
        return super.step ();
    }

    public void assign (MNode model)
    {
        if (inner != null) inner.assign (model);
        for (IteratorIndexed child : children) child.assign (model);
    }

    public void save (MNode study)
    {
        if (inner != null) inner.save (study);
        MNode n = node (study);
        n.set (index, "groupIndex");
        for (IteratorIndexed child : children) child.save (study);
    }

    public void load (MNode study)
    {
        if (inner != null) inner.load (study);
        MNode n = node (study);
        index = n.getInt ("groupIndex");
        for (IteratorIndexed child : children) child.load (study);
    }

    public boolean usesRandom ()
    {
        for (IteratorIndexed child : children) if (child.usesRandom ()) return true;
        return false;
    }
}
