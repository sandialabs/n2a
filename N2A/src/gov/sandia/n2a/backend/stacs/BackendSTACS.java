/*
Copyright 2022-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.stacs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class BackendSTACS extends Backend
{
    @Override
    public String getName ()
    {
        return "STACS";
    }

    @Override
    public void start (final MNode job)
    {
        Thread t = new JobSTACS (job);
        t.setDaemon (true);
        t.start ();
    }
}
