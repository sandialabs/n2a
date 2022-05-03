/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
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

    /**
        Returns a compiler factory appropriate for the given host.
        In settings, each host has a path to the chosen compiler.
        Here we determine which compiler it is and provide an appropriate
        factory for setting up compile/link jobs.
        Presumably, this is called outside of EDT, so this function can take
        as much time as needed to set up the factory, including remote calls.
    **/
    public static Compiler.Factory getFactory (Host host) throws Exception
    {
        Object o = host.objects.get ("cxx");
        if (o instanceof Compiler.Factory) return (Compiler.Factory) o;

        // The most simple-minded approach is to guess compiler identity from the executable's name.
        String exePathString = host.config.getOrDefault ("g++", "backend", "c", "cxx");
        Path   exePath       = host.getResourceDir ().getFileSystem ().getPath (exePathString);
        String exeName       = exePath.getFileName ().toString ();
        int pos = exeName.lastIndexOf ('.');
        if (pos > 0) exeName = exeName.substring (0, pos);
        Compiler.Factory f = null;
        switch (exeName.toLowerCase ())
        {
            case "cl": f = new CompilerCL .FactoryCL  (host, exePath); break;
            default:   f = new CompilerGCC.FactoryGCC (host, exePath); break;
        }
        host.objects.put ("cxx", f);
        return f;
    }
}
