/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Host.AnyProcess;
import gov.sandia.n2a.host.Windows;

public class CompilerGCC extends Compiler
{
    public static class Factory implements CompilerFactory
    {
        protected Host    host;
        protected Path    gcc;
        protected float   version;  // major.minor without any sub-minor version
        protected boolean Darwin;   // Indicates the we should use a few quirky options specific to Mac.

        public Factory (Host host, Path gcc)
        {
            this.host = host;
            this.gcc  = gcc;

            // Determine version
            try (AnyProcess proc = host.build (gcc.toString (), "--version").start ();
                 BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
            {
                // Very fragile, but works with current versions of GCC and Clang.
                String line = reader.readLine ();
                String[] pieces = line.split (" ");
                for (String p : pieces)
                {
                    if (! Character.isDigit (p.charAt (0))) continue;
                    pieces = p.split ("\\.");
                    version = Float.valueOf (pieces[0] + "." + pieces[1]);
                    break;
                }
            }
            catch (Exception e) {e.printStackTrace ();}

            // Detect Mac
            try (AnyProcess proc = host.build ("uname").start ();
                 BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
            {
                String line = reader.readLine ();
                String[] pieces = line.split (" ");
                if (pieces[0].equals ("Darwin")) Darwin = true;
            }
            catch (Exception e) {}
        }

        public Compiler compiler (Path localJobDir)
        {
            return new CompilerGCC (host, localJobDir, gcc, Darwin);
        }

        public String suffixBinary ()
        {
            return ".bin";
        }

        public String suffixLibrary (boolean shared)
        {
            if (! shared) return ".a";
            if (host instanceof Windows) return ".dll";
            return ".so";
        }

        public String suffixLibraryWrapper ()
        {
            if (host instanceof Windows) return ".a";
            return "";
        }

        public String suffixDebug ()
        {
            // A separate debug symbol file (.pdb) is not supported by GCC.
            // However, it is supported by clang.
            return "";
        }

        public String prefixLibrary (boolean shared)
        {
            if (! shared) return "lib";
            if (host instanceof Windows) return "";
            return "lib";
        }

        public boolean wrapperRequired ()
        {
            // This is not strictly true. GCC can link without a static wrapper on Windows.
            // However, the naming of the static link library is more consistent than
            // the naming of the associated DLLs, so this choice makes it easier to find
            // resources like FFmpeg.
            return host instanceof Windows;
        }

        public boolean debugRequired ()
        {
            return false;
        }

        public boolean supportsUnicodeIdentifiers ()
        {
            return version >= 10;
        }
    }

    protected Path         gcc;
    protected List<String> settings = new ArrayList<String> ();
    protected String       optimize = "-O3";
    protected boolean      Darwin;

    public CompilerGCC (Host host, Path localJobDir, Path gcc, boolean Darwin)
    {
        super (host, localJobDir);
        this.gcc    = gcc;
        this.Darwin = Darwin;

        settings.add ("-std=c++11");
        settings.add ("-ffunction-sections");
        settings.add ("-fdata-sections");
    }

    public Path compile () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        command.add (gcc.toString ());
        command.add ("-c");
        addDebugCompile (command);
        if (profiling) command.add ("-pg");
        if (shared) command.add ("-fpic");  // Could use -fPIC, but that option is specifically to avoid size limitations in global offset table that don't apply to any processor we are interested in.
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

    public void addDebugCompile (List<String> command)
    {
        if (debug) command.add ("-g");
        else       command.add (optimize);
    }

    public void addDebugLink (List<String> command)
    {
    }

    public Path compileLink () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        command.add (gcc.toString ());
        addDebugCompile (command);
        if (profiling) command.add ("-pg");
        if (shared)
        {
            command.add ("-fpic");
            command.add ("-shared");
        }
        // else static. We don't use the "-static" flag because it is OK for the resulting binary to depend on some shared code, just not our own runtime.
        for (String setting : settings) command.add (setting);
        if (Darwin) command.add ("-Wl,-dead_strip");
        else        command.add ("-Wl,--gc-sections");

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
        for (String library : libraries)
        {
            command.add ("-l" + library);
        }
        for (Path libraryDir : libraryDirs)
        {
            command.add ("-L" + host.quote (libraryDir));
        }

        command.add ("-o");
        command.add (host.quote (output));

        return runCommand (command);
    }

    public Path linkLibrary () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        if (shared)
        {
            command.add (gcc.toString ());
            command.add ("-shared");
            addDebugLink (command);
            if (host instanceof Windows)  // GCC is capable of generating a wrapper library, but does not require one when linking with other code.
            {
                String stem = output.getFileName ().toString ();
                if (stem.endsWith (".dll")) stem = stem.substring (0, stem.length () - 4);
                Path wrapper = Paths.get (stem + ".a");
                Path parent = output.getParent ();
                if (parent != null) wrapper = parent.resolve (wrapper);

                // export-all-symbols -- This is the default unless there is an explicit export in the
                // code. When using JNI on Windows, there are explicit exports, so this is necessary.
                // out-implib -- Output a static wrapper library. This isn't needed by GCC, but makes
                // the output useful for MSVC.
                // TODO: The linker can't handle spaces in the output path, even when quoted.
                // This may be due to how GCC passes the parameters on to LD.
                command.add ("-Wl,--export-all-symbols,--out-implib," + host.quote (wrapper));
            }
            for (Path object : objects)
            {
                command.add (host.quote (object));
            }
            for (String library : libraries)
            {
                command.add ("-l" + library);
            }
            for (Path libraryDir : libraryDirs)
            {
                command.add ("-L" + host.quote (libraryDir));
            }
            command.add ("-o");
            command.add (host.quote (output));
        }
        else
        {
            String prefix = gcc.getFileName ().toString ();
            prefix        = prefix.substring (0, prefix.length () - 3);  // strip off "g++" or "gcc"
            Path   parent = gcc.getParent ();
            Path ar;
            if (parent == null) ar = Paths.get (prefix + "gcc-ar");       // The usual case, where g++ is specified alone.
            else                ar = parent.resolve (prefix + "gcc-ar");  // g++ is prefixed by at least one path element.
            command.add (ar.toString ());
            command.add ("rsc");  // operation=r (insert members, with replacement); modifier=s (create symbol table); modifier=c (expecting to create archive, so don't warn)
            command.add (host.quote (output));
            for (Path object : objects) command.add (host.quote (object));
        }
        return runCommand (command);
    }
}
