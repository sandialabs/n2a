/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import gov.sandia.n2a.host.Host;

public class CompilerGCC extends Compiler
{
    public static class FactoryGCC implements Factory
    {
        protected Host host;
        protected Path gcc;

        public FactoryGCC (Host host, Path gcc)
        {
            this.host = host;
            this.gcc  = gcc;
        }

        public Compiler make (Path localJobDir)
        {
            return new CompilerGCC (host, localJobDir, gcc);
        }

        public String suffixBinary ()
        {
            return ".bin";
        }

        public String suffixLibraryStatic ()
        {
            return ".a";
        }

        public String suffixLibraryShared ()
        {
            return ".so";
        }
    }

    protected Path         gcc;
    protected List<String> settings = new ArrayList<String> ();
    protected String       optimize = "-O3";

    public CompilerGCC (Host host, Path localJobDir, Path gcc)
    {
        super (host, localJobDir);
        this.gcc = gcc;

        settings.add ("-std=c++17");
        settings.add ("-ffunction-sections");
        settings.add ("-fdata-sections");
    }

    public Path compile () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        command.add (gcc.toString ());
        command.add ("-c");
        if (debug) command.add ("-g");
        else       command.add (optimize);
        if (profiling) command.add ("-pg");
        for (String setting : settings) command.add (setting);

        for (Entry<String,String> define : defines.entrySet ())
        {
            String key   = define.getKey ();
            String value = define.getValue ();
            if (value.isBlank ()) command.add ("-D" + key);
            else                  command.add ("-D" + key + "=" + value);
        }
        for (Path include : includes)
        {
            command.add ("-I" + host.quote (include));
        }
        for (Path source : sources)
        {
            command.add (host.quote (source));
        }

        command.add ("-o");  // goes with next line ...
        command.add (host.quote (output));

        return runCommand (command);
    }

    public Path compileLink () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        command.add (gcc.toString ());
        if (debug) command.add ("-g");
        else       command.add (optimize);
        if (profiling) command.add ("-pg");
        for (String setting : settings) command.add (setting);
        command.add ("-Wl,--gc-sections");

        for (Entry<String,String> define : defines.entrySet ())
        {
            String key   = define.getKey ();
            String value = define.getValue ();
            if (value.isBlank ()) command.add ("-D" + key);
            else                  command.add ("-D" + key + "=" + value);
        }
        for (Path include : includes)
        {
            command.add ("-I" + host.quote (include));
        }
        for (Path source : sources)
        {
            command.add (host.quote (source));
        }
        for (Path object : objects)
        {
            command.add (host.quote (object));
        }
        for (Path library : libraries)
        {
            command.add ("-l" + host.quote (library));
        }
        for (Path libraryDir : libraryDirs)
        {
            command.add ("-L" + host.quote (libraryDir));
        }

        command.add ("-o");
        command.add (host.quote (output));

        return runCommand (command);
    }

    public Path linkLibrary (boolean shared) throws Exception
    {
        String prefix = gcc.getFileName ().toString ();
        prefix        = prefix.substring (0, prefix.length () - 3);  // strip off "g++" or "gcc"

        if (shared)
        {
            throw new Exception ("Shared library not yet implemented");
        }
        else
        {
            Path ar = gcc.getParent ().resolve (prefix + "ar");
            List<String> command = new ArrayList<String> ();
            command.add (ar.toString ());
            command.add ("rsc");  // operation=r (insert members, with replacement); modifier=s (create symbol table); modifier=c (expecting to create archive, so don't warn)
            command.add (host.quote (output));
            for (Path object : objects) command.add (host.quote (object));
            return runCommand (command);
        }
    }
}
