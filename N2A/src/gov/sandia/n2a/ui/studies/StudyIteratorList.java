/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.util.Arrays;
import java.util.List;

import gov.sandia.n2a.db.MNode;

public class StudyIteratorList extends StudyIteratorIndexed
{
    protected List<String> items;

    public StudyIteratorList (String[] keys, String items)
    {
        super (keys);
        this.items = Arrays.asList (items.split (","));
        count = this.items.size ();
    }

    public void assign (MNode model)
    {
        if (inner != null) inner.assign (model);
        model.set (items.get (index), keyPath);
    }
}
