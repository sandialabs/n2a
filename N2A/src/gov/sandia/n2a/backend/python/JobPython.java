/*
Copyright 2020-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.python;

import java.nio.file.Path;

import gov.sandia.n2a.backend.c.JobC;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class JobPython extends Thread
{
    // TODO: copy JobC and rewrite for Python

    public Path runtimeDir; // local or remote

    /**
        Places resources specific to this backend into runtimeDir.
        runtimeDir must be set before calling this function.
    **/
    public boolean unpackRuntime () throws Exception
    {
        boolean changed = false;
        Path n2aDir = runtimeDir.resolve ("n2a");
        if (JobC.unpackRuntime (JobPython.class, null, runtimeDir, "runtime/",     "setup.py")) changed = true;
        if (JobC.unpackRuntime (JobPython.class, null, n2aDir,     "runtime/n2a/", "__init__.py", "MNode.py", "OutputHolder.py", "OutputParser.py", "runtime.py")) changed = true;
        return changed;
    }

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
        // This assumes rather extensive support for Unicode in Python 3.
        // We don't bother supporting Python 2.
        return prefix + NodePart.validIdentifierFrom (input).replaceAll (" ", "_");
    }

    public String resolve (VariableReference r, RendererPython context, boolean lvalue)
    {
        return mangle (r.variable);
    }
}
