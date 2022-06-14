/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class CompilerCL extends Compiler
{
    public static class FactoryCL implements Factory
    {
        protected Host   host;
        protected Path   cl;
        protected Path   MSVCroot; // Visual Studio
        protected Path   SDKroot;  // Windows SDK
        protected String SDKversion;
        protected String arch;

        public FactoryCL (Host host, Path cl)
        {
            this.host = host;
            this.cl   = cl;

            // Probe for include dirs

            //   Visual Studio include -- found by relative path
            //   MSVC
            //      include
            //      lib
            //      bin
            //         Hostx##
            //            x##
            //               cl.exe
            Path temp = cl.getParent ();  // First determine target architecture.
            arch      = temp.getFileName ().toString ();
            MSVCroot  = temp.getParent ().getParent ().getParent ();  // Then locate MSVC root.

            //   UniveralCRT include -- requires probing the registry
            //   See MSVC\Common7\Tools\vsdevcmd\core\winsdk.bat
            String SDKrootName = null;
            for (String HK : new String[] {"HKLM\\SOFTWARE\\Wow6432Node", "HKCU\\SOFTWARE\\Wow6432Node", "HKLM\\SOFTWARE", "HKCU\\SOFTWARE"})
            {
                HK += "\\Microsoft";
                SDKrootName = getRegistryValue (HK + "\\Windows Kits\\Installed Roots", "KitsRoot10");
                if (SDKrootName != null) break;
            }
            if (SDKrootName == null)
            {
                Backend.err.get ().println ("ERROR: failed to locate UniversalCRT SDK");
                throw new Backend.AbortRun ();
            }
            SDKroot = Paths.get (SDKrootName);

            //   Scan the root include dir for specific versions.
            int[] bestVersion = new int[4];
            try
            {
                for (Path p : Files.newDirectoryStream (SDKroot.resolve ("Include")))
                {
                    if (! Files.isDirectory (p)) continue;
                    String fileName = p.getFileName ().toString ();
                    String[] pieces = fileName.split ("\\.");
                    boolean found = false;
                    for (int i = 0; i < pieces.length  &&  i < bestVersion.length; i++)
                    {
                        int v = Integer.valueOf (i);
                        if (found) bestVersion[i] = v;
                        if (bestVersion[i] == v) continue;  // includes "found" case
                        // Not yet found, and versions are unequal.
                        if (v < bestVersion[i]) break;
                        // v > bestVersion and every previous element was equal.
                        found = true;
                        bestVersion[i] = v;
                    }
                    if (found) SDKversion = p.getFileName ().toString ();  // should also verify that corresponding lib dir exists
                }
            }
            catch (IOException e) {}
            if (SDKversion == null)
            {
                Backend.err.get ().println ("ERROR: failed to find UniversalCRT minor version");
                throw new Backend.AbortRun ();
            }
        }

        public String getRegistryValue (String nodePath, String name)
        {
            ProcessBuilder pb = new ProcessBuilder ("reg", "query", "\"" + nodePath + "\"", "/v", "\"" + name + "\"");
            try
            {
                Process p = pb.start ();
                try (BufferedReader reader = new BufferedReader (new InputStreamReader (p.getInputStream ())))
                {
                    String line;
                    while ((line = reader.readLine ()) != null)
                    {
                        line = line.trim ();
                        if (line.startsWith (name)) return line.split ("REG_SZ", 2)[1].trim ();
                    }
                }
            }
            catch (IOException e) {}
            return null;
        }

        public Compiler make (Path localJobDir)
        {
            return new CompilerCL (host, localJobDir, cl, MSVCroot, SDKroot, SDKversion, arch);
        }

        public String suffixBinary ()
        {
            return ".exe";
        }

        public String suffixLibraryStatic ()
        {
            return ".lib";
        }

        public String suffixLibraryShared ()
        {
            return ".dll";
        }
    }

    protected Path         cl;
    protected List<String> settings = new ArrayList<String> ();

    public CompilerCL (Host host, Path localJobDir, Path cl, Path MSVCroot, Path SDKroot, String version, String arch)
    {
        super (host, localJobDir);
        this.cl = cl;

        addInclude (MSVCroot.resolve ("include"));
        addLibraryDir (MSVCroot.resolve ("lib").resolve (arch));

        Path SDKinclude = SDKroot.resolve ("Include").resolve (version);
        addInclude (SDKinclude.resolve ("ucrt"));
        addInclude (SDKinclude.resolve ("um")); // Windows time functions in runtime.tcc
        addInclude (SDKinclude.resolve ("shared"));
        Path SDKlib = SDKroot.resolve ("Lib").resolve (version);
        addLibraryDir (SDKlib.resolve ("ucrt").resolve (arch));
        addLibraryDir (SDKlib.resolve ("um").resolve (arch));

        settings.add ("/nologo");
        settings.add ("/utf-8");
        settings.add ("/EHa");   // enable SEH exception handling
        settings.add ("/GR-");   // no RTTI
        settings.add ("/GL");    // whole-program optimization
        settings.add ("/Gy");    // function-level linking
        settings.add ("/Gw");    // whole-program global data optimization
        // The linker option /OPT:REF removes unused sections. It is on by default, except when debug is enabled.
    }

    public Path compile () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        command.add (cl.toString ());
        command.add ("/c");
        if (debug) command.add ("/Zi");
        else       command.add ("/O2");
        for (String setting : settings) command.add (setting);

        for (Entry<String,String> define : defines.entrySet ())
        {
            String key   = define.getKey ();
            String value = define.getValue ();
            if (value.isBlank ()) command.add ("/D" + key);
            else                  command.add ("/D" + key + "=" + value);
        }
        for (Path include : includes)
        {
            command.add ("/I" + include);
        }
        for (Path source : sources)
        {
            command.add (source.toString ());
        }

        command.add ("/Fo" + output);

        return runCommand (command);
    }

    public Path compileLink () throws Exception
    {
        List<String> command = new ArrayList<String> ();
        command.add (cl.toString ());
        if (debug) command.add ("/Zi");
        else       command.add ("/O2");
        for (String setting : settings) command.add (setting);

        for (Entry<String,String> define : defines.entrySet ())
        {
            String key   = define.getKey ();
            String value = define.getValue ();
            if (value.isBlank ()) command.add ("/D" + key);
            else                  command.add ("/D" + key + "=" + value);
        }
        for (Path include : includes)
        {
            command.add ("/I" + include);
        }
        for (Path source : sources)
        {
            command.add (source.toString ());
        }
        Path parent = output.getParent ();
        command.add ("/Fo" + parent + "\\");  // Set directory for intermediate object file(s). Path only works on Windows, but that should always be true of CL.

        // All remaining items are passed to linker
        command.add ("/link");
        if (profiling) command.add ("/profile");
        for (Path object : objects)
        {
            command.add (object.toString ());
        }
        for (Path library : libraries)
        {
            command.add (library.toString ());
        }
        for (Path libraryDir : libraryDirs)
        {
            command.add ("/libpath:" + libraryDir);
        }
        command.add ("/out:" + output);

        Path out = runCommand (command);

        // Try to clean up .obj file left by the compile step.
        String stem = output.getFileName ().toString ();
        int pos = stem.lastIndexOf (".");
        if (pos > 0) stem = stem.substring (0, pos);
        Files.deleteIfExists (parent.resolve (stem + ".obj"));

        return out;
    }

    public Path linkLibrary (boolean shared) throws Exception
    {
        // Find link program
        Path lib = cl.getParent ().resolve ("lib");

        if (shared)
        {
            throw new Exception ("Shared library not yet implemented");
        }
        else
        {
            List<String> command = new ArrayList<String> ();
            command.add (lib.toString ());
            command.add ("/nologo");
            for (Path object : objects) command.add (object.toString ());
            command.add ("/out:" + output);
            return runCommand (command);
        }
    }
}
