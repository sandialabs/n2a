/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Windows;
import gov.sandia.n2a.host.Host.AnyProcess;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class BackendC extends Backend
{
    protected static HashMap<Host,Object> locks = new HashMap<Host,Object> ();

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
    public HashSet<String> forbiddenSuffixes ()
    {
        return SettingsC.instance.forbiddenSuffixes;
    }

    /**
        Returns a compiler factory appropriate for the given host.
        In settings, each host has a path to the chosen compiler.
        Here we determine which compiler it is and provide an appropriate
        factory for setting up compile/link jobs.
        Presumably, this is called outside of EDT, so this function can take
        as much time as needed to set up the factory, including remote calls.
    **/
    public static CompilerFactory getFactory (Host host) throws Exception
    {
        Object o = host.objects.get ("cxx");
        if (o instanceof CompilerFactory) return (CompilerFactory) o;

        String exePathString = host.config.get ("backend", "c", "cxx");
        if (exePathString.isBlank ())  // User did not specify compiler, so probe the system for common options.
        {
            exePathString = "g++";  // Fallback value.
            if (host instanceof Windows)
            {
                try (AnyProcess proc = host.build ("C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere", "-latest", "-utf8", "-format", "json").start ();
                     BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
                {
                    MNode result = new MVolatile ();
                    new JSON ().read (result, reader);
                    if (! result.isEmpty ())
                    {
                        // result contains a list of installations. We take the first one.
                        Path base = Paths.get (result.get (0, "installationPath"));
                        // Now tack on the appropriate paths. See CompilerCL.java for comments about directory structure.
                        Path MSVC = base.resolve ("VC").resolve ("Tools").resolve ("MSVC");
                        //   Unfortunately, there doesn't seem to be a simple way to get the right version number.
                        TreeSet<String> sorted = new TreeSet<String> ();
                        try (DirectoryStream<Path> dir = Files.newDirectoryStream (MSVC))
                        {
                            for (Path p : dir) sorted.add (p.getFileName ().toString ());
                        }
                        Path ver = MSVC.resolve (sorted.last ());
                        exePathString = ver.resolve ("bin").resolve ("Hostx64").resolve ("x64").resolve ("cl.exe").toString ();
                    }
                }
                catch (Exception e) {}
            }
        }

        // The most simple-minded approach is to guess compiler identity from the executable's name.
        Path   exePath       = host.getResourceDir ().getFileSystem ().getPath (exePathString);
        String exeName       = exePath.getFileName ().toString ();
        int pos = exeName.lastIndexOf ('.');
        if (pos > 0) exeName = exeName.substring (0, pos);
        exeName = exeName.toLowerCase ();
        CompilerFactory f = null;
        if      (exeName.equals     ("cl"   )) f = new CompilerCL   .Factory (host, exePath);
        else if (exeName.startsWith ("clang")) f = new CompilerClang.Factory (host, exePath);
        else                                   f = new CompilerGCC  .Factory (host, exePath);
        host.objects.put ("cxx", f);
        return f;
    }

    /**
        @return An object suitable for synchronizing the rebuild of the C backend on a given host.
    **/
    public static Object getLock (Host host)
    {
        synchronized (locks)
        {
            Object result = locks.get (host);
            if (result == null)
            {
                result = new Object ();
                locks.put (host, result);
            }
            return result;
        }
    }
}
