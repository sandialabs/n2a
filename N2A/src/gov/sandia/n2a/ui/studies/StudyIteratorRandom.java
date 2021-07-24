/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;

public class StudyIteratorRandom extends StudyIteratorIndexed
{
    protected Operator expression;
    protected Type     nextValue;

    public StudyIteratorRandom (String[] keys, String value, MNode n)
    {
        super (keys);
        count = n.getOrDefault (1, "count");

        try
        {
            expression = Operator.parse (value);
        }
        catch (Exception e)
        {
            // TODO: some form of error reporting for Study.
        }
    }

    public boolean step ()
    {
        index++;
        nextValue = expression.eval (null);
        return index < count;
    }

    public void assign (MNode model)
    {
        if (inner != null) inner.assign (model);
        model.set (nextValue, keyPath);
    }
}
