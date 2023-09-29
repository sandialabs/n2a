/*
Copyright 2020-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    protected double step       = 1;
    protected String rangeUnits = "";

    public IteratorRange (String[] keys, String range)
    {
        super (keys);

        String[] pieces = range.split ("]", 2);
        if (pieces.length == 2) rangeUnits = pieces[1];
        range = pieces[0];

        pieces = range.split (",");
        hi = new UnitValue (pieces[0]).get ();
        if (pieces.length > 1)
        {
            lo = hi;
            hi = new UnitValue (pieces[1]).get ();
        }
        if (pieces.length > 2)
        {
            step = new UnitValue (pieces[2]).get ();
        }

        count = (int) Math.floor ((hi - lo) / step) + 1;  // basic formula
        // Compensate for finite precision
        double epsilon = 1e-6;
        double beyond = lo + step * count;  // This should be one full step past hi, but if hi falls slightly short of a step quantum, this could be slightly greater than hi. It will never be less than hi.
        if ((beyond - hi) / step < epsilon) count++;
    }

    public void assign (MNode model)
    {
        if (inner != null) inner.assign (model);
        model.set (Scalar.print (lo + step * index) + rangeUnits, keyPath);
    }
}
