/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.python;

import gov.sandia.n2a.backend.c.JobC;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;

public class JobPython extends Thread
{
    // TODO: copy JobC and rewrite for Python

    public String mangle (Variable v)
    {
        return mangle (v.nameString ());
    }

    public String mangle (String prefix, Variable v)
    {
        return mangle (prefix, v.nameString ());
    }

    public String mangle (String input)
    {
        return mangle ("_", input);
    }

    public String mangle (String prefix, String input)
    {
        // JobC.mangle() is also suitable for python.
        return JobC.mangle (prefix, input);
    }

    public String resolve (VariableReference r, RendererPython context, boolean lvalue)
    {
        return mangle (r.variable);
    }
}
