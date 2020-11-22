/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class BackendC extends Backend
{
    @Override
    public String getName ()
    {
        return "C";
    }

    @Override
    public void start (final MNode job)
    {
        Thread t = new JobC (job);
        t.setDaemon (true);
        t.start ();
    }

    @Override
    public double currentSimTime (MNode job)
    {
        return InternalBackend.getSimTimeFromOutput (job, "out", 0);
    }
}
