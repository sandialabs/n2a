/*
Copyright 2013,2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterDomain;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class BackendC extends Backend
{
    @Override
    public String getName ()
    {
        return "C";
    }

    @Override
    public ParameterDomain getSimulatorParameters ()
    {
        ParameterDomain result = new ParameterDomain ();
        result.addParameter (new Parameter ("duration",     "1.0"  ));  // default is 1 second
        result.addParameter (new Parameter ("c.integrator", "Euler"));  // alt is "RungeKutta"
        return result;
    }

    @Override
    public ParameterDomain getOutputVariables (MNode model)
    {
        try
        {
            if (model == null) return null;
            EquationSet s = new EquationSet (model);
            if (s.name.length () < 1) s.name = "Model";
            s.resolveLHS ();
            return s.getOutputParameters ();
        }
        catch (Exception error)
        {
            return null;
        }
    }

    @Override
    public void execute (final MNode job)
    {
        Thread t = new Thread ("BackendC.execute")
        {
            @Override
            public void run ()
            {
                String jobDir = new File (job.get ()).getParent ();  // assumes the MNode "job" is really an MDoc. In any case, the value of the node should point to a file on disk where it is stored in a directory just for it.
                try {err.set (new PrintStream (new FileOutputStream (new File (jobDir, "err"), true)));}
                catch (FileNotFoundException e) {}

                try
                {
                    new JobC ().execute (job);
                }
                catch (AbortRun a)
                {
                }
                catch (Exception e)
                {
                    e.printStackTrace (Backend.err.get ());
                }

                PrintStream ps = err.get ();
                if (ps != System.err) ps.close ();
            }
        };
        t.setDaemon (true);
        t.start ();
    }

    @Override
    public double currentSimTime (MNode job)
    {
        return InternalBackend.getSimTimeFromOutput (job);
    }
}
