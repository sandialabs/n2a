/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.MNode;

public class StudyIteratorRange extends StudyIteratorIndexed
{
    protected double lo;
    protected double hi;
    protected double step;

    public StudyIteratorRange (String[] keys, String range)
    {
        super (keys);
        String[] pieces = range.split (",");
        lo = Double.valueOf (pieces[0]);
        if (pieces.length > 1) hi = Double.valueOf (pieces[0]);
        else                   hi = lo;
        if (pieces.length > 2)
        {
            step = hi;
            hi = Double.valueOf (pieces[2]);
        }
        else
        {
            step = 1;
        }
        count = (int) Math.floor ((hi - lo) / step);
    }

    public void assign (MNode model)
    {
        if (inner != null) inner.assign (model);
        model.set (lo + step * index, keyPath);
    }
}
