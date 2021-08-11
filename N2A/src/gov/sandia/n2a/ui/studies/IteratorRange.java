/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Scalar;

public class IteratorRange extends IteratorIndexed
{
    protected double lo;
    protected double hi;
    protected double step;

    public IteratorRange (String[] keys, String range)
    {
        super (keys);
        String[] pieces = range.split (",");
        lo = new UnitValue (pieces[0]).get ();
        if (pieces.length > 1) hi = new UnitValue (pieces[1]).get ();
        else                   hi = lo;
        if (pieces.length > 2)
        {
            step = hi;
            hi = new UnitValue (pieces[2]).get ();
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
        model.set (Scalar.print (lo + step * index), keyPath);
    }
}
