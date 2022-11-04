/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Host.AnyProcess;
import gov.sandia.n2a.host.Host.AnyProcessBuilder;
import gov.sandia.n2a.plugins.extpoints.Backend;

public abstract class Compiler
{
    protected Host               host;
    protected Path               localJobDir;
    protected Map<String,String> defines     = new HashMap<String,String> ();
    protected List<Path>         includes    = new ArrayList<Path> ();
    protected List<Path>         sources     = new ArrayList<Path> ();
    protected List<Path>         objects     = new ArrayList<Path> ();
    protected List<String>       libraries   = new ArrayList<String> ();
    protected List<Path>         libraryDirs = new ArrayList<Path> ();
    protected Path               output;
    protected boolean            debug;
    protected boolean            profiling;
    protected boolean            shared;

    public Compiler (Host host, Path localJobDir)
    {
        this.host        = host;
        this.localJobDir = localJobDir;
    }

    public void addDefine (String name)
    {
        defines.put (name, "");
    }

    public void addDefine (String name, String value)
    {
        defines.put (name, value);
    }

    public void addInclude (Path path)
    {
        includes.add (path);
    }

    public void addSource (Path path)
    {
        sources.add (path);
    }

    public void addObject (Path path)
    {
        objects.add (path);
    }

    /**
        @param path The stem name of the library, undecorated by prefix or suffix.
        The linker or this class is responsible to add sufficient decorations to
        find the exact file name. If you need a specific path, specify the directory
        that contains the library with a call to addLibraryDir().
    **/
    public void addLibrary (String path)
    {
        libraries.add (path);
    }

    public void addLibraryDir (Path path)
    {
        libraryDirs.add (path);
    }

    public void setOutput (Path path)
    {
        output = path;
    }

    public void setDebug ()
    {
        debug = true;
    }

    public void setProfiling ()
    {
        profiling = true;
    }

    public void setShared ()
    {
        shared = true;
    }

    public abstract Path compile     () throws Exception;  // returns file that captured the compiler's stdout
    public abstract Path compileLink () throws Exception;  // ditto
    public abstract Path linkLibrary () throws Exception;  // ditto

    public Path runCommand (List<String> command) throws Exception
    {
        // Useful for debugging. The dumped command can be used directly in a terminal to diagnose stalled builds.
        System.out.println (String.join (" ", command));

        // Remove empty strings from command. This is a convenience to the caller,
        // allowing arguments to be conditionally omitted with the ternary operator.
        for (int i = command.size () - 1; i >= 0; i--)
        {
            String s = command.get (i);
            if (s.isEmpty ()) command.remove (i);
        }

        Path out = localJobDir.resolve ("compile.out");
        Path err = localJobDir.resolve ("compile.err");

        AnyProcessBuilder b = host.build (command);
        b.redirectOutput (out);  // Should truncate existing files.
        b.redirectError  (err);
        try (AnyProcess p = b.start ())
        {
            p.waitFor ();

            if (p.exitValue () != 0)
            {
                PrintStream ps = Backend.err.get ();
                ps.println ("Failed to compile:");
                ps.print (Host.streamToString (Files.newInputStream (err)));
                ps.print (Host.streamToString (Files.newInputStream (out)));
                Files.delete (out);
                Files.delete (err);
                throw new Backend.AbortRun ();
            }

            String errString = Host.streamToString (Files.newInputStream (err));
            if (! errString.isEmpty ())
            {
                PrintStream ps = Backend.err.get ();
                ps.println ("Compiler says:");
                ps.println (errString);
            }
            Files.delete (err);
        }
        return out;
    }
}
