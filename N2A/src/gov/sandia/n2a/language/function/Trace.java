/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.Wrapper;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Trace extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "trace";
            }

            public Operator createInstance ()
            {
                return new Trace ();
            }
        };
    }

    public boolean isOutput ()
    {
        return true;
    }

    public Type eval (Instance context)
    {
        Scalar result = (Scalar) operands[0].eval (context);

        Wrapper wrapper = null;
        if (context instanceof InstanceTemporaries)
        {
            wrapper = ((InstanceTemporaries) context).simulator.wrapper;
        }
        else
        {
            Instance top = context;
            while (top.container != null) top = top.container;
            if (top instanceof Wrapper) wrapper = (Wrapper) top;
        }
        if (wrapper != null)
        {
            String column = operands[1].eval (context).toString ();
            Integer index = wrapper.columnMap.get (column);
            if (index == null)
            {
                wrapper.columnMap.put (column, wrapper.columnValues.size ());
                wrapper.columnValues.add ((float) result.value);
            }
            else
            {
                wrapper.columnValues.set (index, (float) result.value);
            }
        }

        return result;
    }

    public String toString ()
    {
        return "trace";
    }
}
