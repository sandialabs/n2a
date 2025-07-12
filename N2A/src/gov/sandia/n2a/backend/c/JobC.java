/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.backend.internal.InternalBackendData.EventSource;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.Conversion;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.host.Windows;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.EquationSet.ConnectionMatrix;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Delay;
import gov.sandia.n2a.language.function.Draw;
import gov.sandia.n2a.language.function.Draw3D;
import gov.sandia.n2a.language.function.DrawLight;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Mfile;
import gov.sandia.n2a.language.function.Mmatrix;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadImage;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.MultiplyElementwise;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddPart;
import gov.sandia.n2a.ui.jobs.NodeJob;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

public class JobC extends Thread
{
    protected static Map<Host,Set<String>> runtimeBuilt = new HashMap<Host,Set<String>> ();  // collection of Hosts for which runtime has already been checked/built during this session

    public    MNode           job;
    protected EquationSet     digestedModel;
    public    ExponentContext exponentContext;
    protected MVolatile       params;

    public Host env;
    public Path localJobDir;
    public Path jobDir;     // local or remote
    public Path runtimeDir; // local or remote

    protected Path        ffmpegLibDir;  // Where link libraries are found
    protected Path        ffmpegIncDir;
    protected Path        ffmpegBinDir;  // If non-null, then shared library dir should be added to path.
    protected Path        jniIncDir;     // jni.h
    protected Path        jniIncMdDir;   // jni_md.h
    protected Set<String> glLibs;
    protected Path        glLibDir;      // If non-null, then this should be added to the library dirs during linkage.

    protected boolean supportsUnicodeIdentifiers;
    public    String  T;
    public    boolean fixedPoint;
    protected String  SIMULATOR;
    protected long    seed;
    protected boolean during;
    protected boolean after;
    protected boolean kokkos;        // profiling method
    public    boolean gprof;         // profiling method
    public    boolean debug;         // compile with debug symbols; applies to current model as well as any runtime components that happen to get rebuilt
    public    boolean cli;           // command-line interface
    protected boolean lib;           // Target is a library rather than executable. Suitable for Python wrapper or other external integration.
    public    String  libStem;       // name of library; only meaningful when lib is true
    public    boolean shared = true; // When lib is false, determines whether target binary uses static or dynamic linking to runtime. When lib is true, determines whether target library is shared or static. Target library always contains full runtime, but will not include external resources like FFmpeg.
    public    boolean csharp;        // Emit library code for use by C# (and other CLR languages). Only has an effect when lib is true.
    protected boolean jni;           // Emit library code for use by Java. Only has an effect when lib is true.
    public    boolean tls;           // Make global objects thread-local, so multiple simulations can be run in same process. (Generally, it is cleaner to use separate process for each simulation, but some users want this.)
    protected boolean usesPolling;
    protected boolean hasMfile;      // Mfile is used somewhere in the model tree. When present, we emit code to set MDoc exception mode.
    protected List<ProvideOperator> extensions = new ArrayList<ProvideOperator> ();

    // These values are unique across the whole simulation, so they go here rather than BackendDataC.
    // Where possible, the key is a String. Otherwise, it is an Operator which is specific to one expression.
    protected HashMap<Object,String> matrixNames      = new HashMap<Object,String> ();
    protected HashMap<Object,String> mfileNames       = new HashMap<Object,String> ();
    protected HashMap<Object,String> inputNames       = new HashMap<Object,String> ();
    protected HashMap<Object,String> outputNames      = new HashMap<Object,String> ();
    protected HashMap<Object,String> imageInputNames  = new HashMap<Object,String> ();
    protected HashMap<Object,String> imageOutputNames = new HashMap<Object,String> ();
    public    HashMap<Object,String> stringNames      = new HashMap<Object,String> ();
    public    HashMap<Object,String> extensionNames   = new HashMap<Object,String> ();  // Shared by all extension-provided operators.

    // Work around the initialization sequencing problem by delaying the call to holderHelper until main().
    // To do this, we need to stash variable names. This may seem redundant with the above maps,
    // but this is a more limited case.
    protected List<Constant>   staticMatrix    = new ArrayList<Constant> ();  // constant matrices that should be statically initialized
    protected List<ReadMatrix> mainMatrix      = new ArrayList<ReadMatrix> ();
    protected List<Mfile>      mainMfile       = new ArrayList<Mfile> ();
    protected List<Input>      mainInput       = new ArrayList<Input> ();
    protected List<Output>     mainOutput      = new ArrayList<Output> ();
    protected List<ReadImage>  mainImageInput  = new ArrayList<ReadImage> ();
    protected List<Draw>       mainImageOutput = new ArrayList<Draw> ();
    public    List<Operator>   mainExtension   = new ArrayList<Operator> ();  // Shared by all extension-provided operators.

    public JobC (MNode job)
    {
        super ("C Job");
        this.job = job;

        // Collect plugin renderers
        List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (ProvideOperator.class);
        for (ExtensionPoint exp : exps) extensions.add ((ProvideOperator) exp);
    }

    public void run ()
    {
        localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
        Path errPath = localJobDir.resolve ("err");
        try {Backend.err.set (new PrintStream (new FileOutputStream (errPath.toFile (), true), false, "UTF-8"));}
        catch (Exception e) {}

        try
        {
            job.set ("Preparing", "status");
            job.set (System.currentTimeMillis (), "started");
            MNode model = NodeJob.getModel (job);

            T = model.getOrDefault ("float", "$meta", "backend", "c", "type");
            if (T.contains ("int"))
            {
                if (T.length () > 3)
                {
                    T = "int";
                    Backend.err.get ().println ("WARNING: For now, only supported integer type is 'int', which is assumed to be signed 32-bit.");
                }
                fixedPoint = true;
            }
            else if (! T.equals ("double")  &&  ! T.equals ("float"))
            {
                T = "float";
                Backend.err.get ().println ("WARNING: Unsupported numeric type. Defaulting to single-precision float.");
            }

            kokkos = model.getFlag ("$meta", "backend", "c", "kokkos");
            gprof  = model.getFlag ("$meta", "backend", "c", "gprof");
            debug  = model.getFlag ("$meta", "backend", "c", "debug");
            cli    = model.getFlag ("$meta", "backend", "c", "cli");
            tls    = model.getFlag ("$meta", "backend", "c", "tls");
            csharp = model.getFlag ("$meta", "backend", "c", "sharp");
            jni    = model.getFlag ("$meta", "backend", "c", "jni");
            if (! lib)  // Model is output as a regular executable/binary. (When "lib" is true, model is output as linkable code.)
            {
                // For an executable, "shared" means that it links to a shared library containing the runtime code.
                // The other case, static, means that all the library code is included in the executable image.
                // "lib" is only set true by ExportC* classes. In that case, they set "shared" based on file dialog.
                if (model.data ("$meta", "backend", "c", "shared")) shared = model.getFlag ("$meta", "backend", "c", "shared");
                if (tls  &&  shared)
                {
                    tls = false;
                    Backend.err.get ().println ("WARNING: TLS is incompatible with separate shared-object runtime. Ignoring this feature.");
                }
            }

            String e = model.get ("$meta", "backend", "all", "event");
            switch (e)
            {
                case "before":
                    during = false;
                    after  = false;
                    break;
                case "after":
                    during = false;
                    after  = true;
                default:  // during
                    during = true;
                    after  = false;
            }

            env              = Host.get (job);
            Path resourceDir = env.getResourceDir ();
            jobDir           = Host.getJobDir (resourceDir, job);  // Unlike localJobDir (which is created by MDir), this may not exist until we explicitly create it.
            runtimeDir       = resourceDir.resolve ("backend").resolve ("c");
            detectExternalResources ();
            rebuildRuntime ();

            Files.createDirectories (jobDir);  // digestModel() might write to a remote file (params), so we need to ensure the dir exists first.
            digestedModel = new EquationSet (model);
            if (cli) params = new MVolatile ();
            digestModel ();
            String duration = digestedModel.metadata.get ("duration");
            if (! duration.isBlank ()) job.set (duration, "duration");
            if (cli)
            {
                try (BufferedWriter writer = Files.newBufferedWriter (jobDir.resolve ("params.json")))
                {
                    JSON json = new JSON ();
                    json.write (params, writer);
                }
                catch (IOException iox) {iox.printStackTrace ();}
            }

            seed = model.getOrDefault (System.currentTimeMillis () & 0x7FFFFFFF, "$meta", "seed");
            job.set (seed, "seed");

            System.out.println (digestedModel.dump (false));

            Path source = jobDir.resolve ("model.cc");
            generateCode (source);

            if (lib)
            {
                makeLibrary (source);
                try {Host.stringToFile ("success", localJobDir.resolve ("finished"));}
                catch (Exception f) {}
            }
            else
            {
                Path commandPath = build (source);

                // The C program could append to the same error file, so we need to close the file before submitting.
                PrintStream ps = Backend.err.get ();
                if (ps != System.err)
                {
                    ps.close ();
                    Backend.err.remove ();
                    job.set (Host.size (errPath), "errSize");
                }

                List<List<String>> commands = new ArrayList<List<String>> ();
                List<String> command = new ArrayList<String> ();
                command.add (env.quote (commandPath));
                commands.add (command);

                List<Path> libPath = new ArrayList<Path> ();
                if (shared) libPath.add (runtimeDir);
                if (ffmpegBinDir != null) libPath.add (ffmpegBinDir);  // This could be redundant with existing system path.

                Backend.copyExtraFiles (model, job);
                env.submitJob (job, env.clobbersOut (), commands, libPath);
            }
            job.clear ("status");
        }
        catch (Exception e)
        {
            PrintStream ps = Backend.err.get ();
            if (ps == System.err)  // Need to reopen err stream.
            {
                try {Backend.err.set (ps = new PrintStream (new FileOutputStream (errPath.toFile (), true), false, "UTF-8"));}
                catch (Exception e2) {}
            }
            if (e instanceof AbortRun)
            {
                String message = e.getMessage ();
                if (message != null) ps.println (message);
            }
            else e.printStackTrace (ps);

            try {Host.stringToFile ("failure", localJobDir.resolve ("finished"));}
            catch (Exception f) {}
        }

        // If an exception occurred, the err file could still be open.
        PrintStream ps = Backend.err.get ();
        if (ps != System.err) ps.close ();
    }

    public static boolean contains (Path dir, String prefix, String name, String suffix)
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream (dir))
        {
            for (Path path : stream)
            {
                String fileName = path.getFileName ().toString ();
                if (! fileName.startsWith (prefix)) continue;
                if (! fileName.endsWith   (suffix)) continue;
                // There is a potential for ambiguity here, if name contains part of the prefix
                // or suffix, but that is unlikely in practice. 
                if (fileName.contains (name)) return true;
            }
        }
        catch (IOException e) {}
        return false;
    }

    @SuppressWarnings("unchecked")
    public void detectExternalResources () throws Exception
    {
        CompilerFactory factory = BackendC.getFactory (env);

        // for finding a shared library ...
        boolean wrap = factory.wrapperRequired ();
        String prefix = factory.prefixLibrary (! wrap);  // If wrap is required, the we must use the static prefix. Otherwise, use shared prefix.
        String suffix = wrap ? factory.suffixLibraryWrapper () : factory.suffixLibrary (true);

        // FFmpeg
        if (env.objects.containsKey ("ffmpegLibDir"))
        {
            ffmpegLibDir = (Path) env.objects.get ("ffmpegLibDir");
            ffmpegIncDir = (Path) env.objects.get ("ffmpegIncDir");
            ffmpegBinDir = (Path) env.objects.get ("ffmpegBinDir");
        }
        else
        {
            // Plan: Detect the libraries, then estimate location of includes from that.
            String ffmpegString = env.config.get ("backend", "c", "ffmpeg");
            if (ffmpegString.isBlank ())  // Search typical locations
            {
                for (String path : new String[] {"ffmpeg/lib", "ffmpeg/bin", "/usr/lib64", "/usr/lib", "/usr/local/lib64", "/usr/local/lib"})
                {
                    ffmpegLibDir = runtimeDir.resolve (path);
                    if (contains (ffmpegLibDir, prefix, "avcodec", suffix)) break;
                    ffmpegLibDir = null;
                }
            }
            else  // User-specified
            {
                // Relative path is resolved w.r.t. the runtime dir. This makes it easy to
                // stash ffmpeg as a subdir of runtime.
                ffmpegLibDir = runtimeDir.resolve (ffmpegString);
                if (! contains (ffmpegLibDir, prefix, "avcodec", suffix)) ffmpegLibDir = null;
            }
            if (ffmpegLibDir != null)
            {
                Path ffmpegDir = ffmpegLibDir.getParent ();
                if (factory.wrapperRequired ()) ffmpegBinDir = ffmpegDir.resolve ("bin");  // Use of wrapper implies that shared library is treated same as a binary, rather than living in the lib directory.
                for (String path : new String[] {"include/ffmpeg", "include"})
                {
                    ffmpegIncDir = ffmpegDir.resolve (path);
                    if (Files.exists (ffmpegIncDir.resolve ("libavcodec/avcodec.h"))) break;
                    ffmpegIncDir = null;
                }
            }

            env.objects.put ("ffmpegLibDir", ffmpegLibDir);
            env.objects.put ("ffmpegIncDir", ffmpegIncDir);
            env.objects.put ("ffmpegBinDir", ffmpegBinDir);
        }

        // TODO: freetype

        // JNI
        if (env.objects.containsKey ("jniIncMdDir"))
        {
            jniIncMdDir = (Path) env.objects.get ("jniIncMdDir");
            jniIncDir   = (Path) env.objects.get ("jniIncDir");
        }
        else
        {
            String jni_md = env.config.get ("backend", "c", "jni_md");
            if (jni_md.isBlank ())
            {
                Path javaHome = Paths.get (System.getProperty ("java.home"));
                jniIncDir = javaHome.resolve ("include");
                if      (Host.isWindows ()) jniIncMdDir = jniIncDir.resolve ("win32");
                else if (Host.isMac ())     jniIncMdDir = jniIncDir.resolve ("darwin"); // don't know if this is universal
                else                        jniIncMdDir = jniIncDir.resolve ("linux");  // ditto
            }
            else
            {
                jniIncMdDir = Paths.get (jni_md);
                jniIncDir   = jniIncMdDir.getParent ();
            }

            if (! Files.exists (jniIncMdDir.resolve ("jni_md.h")))
            {
                jniIncMdDir = null;
                jniIncDir   = null;
            }

            env.objects.put ("jniIncMdDir", jniIncMdDir);
            env.objects.put ("jniIncDir",   jniIncDir);
        }

        // OpenGL
        if (env.objects.containsKey ("glLibs"))
        {
            glLibs   = (Set<String>) env.objects.get ("glLibs");
            glLibDir = (Path)        env.objects.get ("glLibDir");
        }
        else
        {
            String glString = env.config.get ("backend", "c", "gl");
            if (glString.isBlank ())  // Search typical locations
            {
                if (env instanceof Windows)
                {
                    // Since OpenGL32.lib is part of the Windows API, it pretty much has to exist.
                    // However, we still verify existence as a way to avoid an unsolvable build error.
                    if (factory instanceof CompilerCL.Factory)  // link via Windows SDK
                    {
                        CompilerCL.Factory cl = (CompilerCL.Factory) factory;
                        Path SDKlib = cl.SDKroot.resolve ("Lib").resolve (cl.SDKversion);
                        Path um     = SDKlib.resolve ("um").resolve (cl.arch);
                        if (Files.exists (um.resolve ("OpenGL32.lib")))
                        {
                            glLibs = new TreeSet<String> ();
                            glLibs.add ("OpenGL32");
                            glLibs.add ("gdi32");
                            glLibs.add ("user32");
                        }
                    }
                    else if (factory instanceof CompilerGCC.Factory)  // Unix-like library naming, but relative to compiler installation
                    {
                        // The library should be in a directory at the same level as the
                        // bin directory that contains the compiler.
                        // cygwin: lib/w32api/libopengl32.a
                        // msys:   lib/libopengl32.a

                        // Resolve gcc's actual location.
                        Path gcc = ((CompilerGCC.Factory) factory).gcc;
                        gcc = env.which (gcc);

                        if (gcc != null  &&  gcc.getNameCount () > 2)
                        {
                            Path root = gcc.getParent ().getParent ();
                            for (String path : new String[] {"lib", "lib/w32api"})
                            {
                                if (contains (root.resolve (path), prefix, "opengl32", suffix))
                                {
                                    glLibs = new TreeSet<String> ();
                                    glLibs.add ("opengl32");
                                    glLibs.add ("gdi32");
                                    glLibs.add ("user32");
                                    break;
                                }
                            }
                        }
                    }
                }
                else  // Unix-like system
                {
                    for (String path : new String[] {"/usr/lib64", "/usr/lib", "/usr/local/lib64", "/usr/local/lib"})
                    {
                        if (Files.exists (runtimeDir.resolve (path).resolve ("libEGL.so")))  // TODO: create lib names in a more intelligent way
                        {
                            glLibs = new TreeSet<String> ();
                            glLibs.add ("EGL");
                        }
                    }
                }
            }
            else  // User-specified
            {
                Path glLib = runtimeDir.resolve (glString);
                if (Files.exists (glLib))
                {
                    glLibs = new TreeSet<String> ();
                    String glLibName = glLib.getFileName ().toString ();
                    if (glLibName.startsWith (prefix)) glLibName = glLibName.substring (suffix.length ());
                    if (glLibName.endsWith (suffix)) glLibName = glLibName.substring (0, glLibName.length () - suffix.length ());
                    glLibs.add (glLibName);
                    glLibDir = glLib.getParent ();
                }
            }

            env.objects.put ("glLibs",   glLibs);
            env.objects.put ("glLibDir", glLibDir);
        }
    }

    /**
        Utility class for running build jobs in parallel.
        This is mainly to speed up remote connections, but helpful anywhere.
    **/
    public static abstract class ThreadTrap extends Thread
    {
        protected Exception error;
        protected PrintStream err;

        public abstract void doit () throws Exception;

        public void run ()
        {
            try
            {
                Backend.err.set (err);  // This is thread local, so needs to be set here.
                doit ();
            }
            catch (Exception e)
            {
                error = e;
            }
        }
    }

    public static class ThreadGroup
    {
        protected List<ThreadTrap> threads = new ArrayList<ThreadTrap> ();
        protected Exception error;

        public void start (ThreadTrap task)
        {
            threads.add (task);
            task.err = Backend.err.get ();
            task.setDaemon (true);
            task.start ();
        }

        public void joinAndThrow () throws Exception
        {
            for (ThreadTrap task : threads)
            {
                task.join ();
                if (task.error != null) error = task.error;
            }
            threads.clear ();  // So this object can be reused.
            if (error != null) throw error;
        }
    }

    public void rebuildRuntime () throws Exception
    {
        // Prevent jobs from trying to rebuild runtime in parallel.
        // They could interfere with each other.
        synchronized (BackendC.getLock (env))
        {
            // Update runtime source files, if necessary
            boolean changed = false;
            if (env.config.getFlag ("backend", "c", "compilerChanged"))
            {
                changed = true;
                JobC.runtimeBuilt.remove (env);
            }
            Set<String> runtimes = JobC.runtimeBuilt.get (env);
            if (runtimes == null)
            {
                if (unpackRuntime ()) changed = true;
                runtimes = new HashSet<String> ();
                JobC.runtimeBuilt.put (env, runtimes);
            }
            CompilerFactory factory = BackendC.getFactory (env);
            supportsUnicodeIdentifiers = factory.supportsUnicodeIdentifiers ();
            String runtimeName = factory.prefixLibrary (shared) + runtimeName () + factory.suffixLibrary (shared);
            Path runtimeLib = runtimeDir.resolve (runtimeName);
            for (ProvideOperator pf : extensions)
            {
                if (pf.rebuildRuntime (this))  // Returns true of a new plugin object file was built.
                {
                    // Need to incorporate plugin object into runtime library, so force rebuild.
                    runtimes.remove (runtimeName);
                    Files.deleteIfExists (runtimeLib);
                }
            }
            env.config.clear ("backend", "c", "compilerChanged");

            if (changed)  // Delete all existing object files and runtime libs.
            {
                runtimes.clear ();
                try (DirectoryStream<Path> list = Files.newDirectoryStream (runtimeDir))
                {
                    for (Path file : list)
                    {
                        String fileName = file.getFileName ().toString ();
                        if (fileName.endsWith (".o")  ||  fileName.contains ("runtime_"))  // The underscore after "runtime" is crucial.
                        {
                            job.set ("deleting " + file, "status");
                            Files.delete (file);
                        }
                    }
                }
                catch (IOException e) {}
            }

            if (runtimes.contains (runtimeName)) return;

            // Compile runtime
            List<String> sources = new ArrayList<String> ();  // List of source names
            sources.add ("runtime");
            sources.add ("holder");
            sources.add ("MNode");
            sources.add ("profiling");
            if (fixedPoint) sources.add ("fixedpoint");
            sources.add ("CanvasImage");
            sources.add ("Image");
            sources.add ("ImageFileFormat");
            sources.add ("ImageFileFormatBMP");
            sources.add ("PixelBuffer");
            sources.add ("PixelFormat");
            if (ffmpegLibDir != null)
            {
                sources.add ("Video");
                sources.add ("VideoFileFormatFFMPEG");
            }
            if (jniIncMdDir != null  &&  shared  &&  ! (env instanceof Remote))  // Only add JNI to shared runtime library on localhost.
            {
                sources.add ("NativeResource");
            }

            ThreadGroup tg = new ThreadGroup ();
            for (String stem : sources)
            {
                String objectName = objectName (stem);
                Path object = runtimeDir.resolve (objectName);
                if (Files.exists (object)) continue;

                tg.start (new ThreadTrap ()
                {
                    public void doit () throws Exception
                    {
                        job.set ("Compiling " + objectName, "status");

                        Compiler c = factory.compiler (localJobDir);
                        if (shared) c.setShared ();
                        if (debug ) c.setDebug ();
                        if (gprof ) c.setProfiling ();
                        addIncludes (c);
                        c.addDefine ("n2a_T", T);
                        if (fixedPoint) c.addDefine ("n2a_FP");
                        if (tls) c.addDefine ("n2a_TLS");
                        c.addSource (runtimeDir.resolve (stem + ".cc"));
                        c.setOutput (object);

                        Path out = c.compile ();
                        Files.delete (out);
                    }
                });
            }
            tg.joinAndThrow ();

            // Link the runtime objects into a single shared library.
            if (shared)
            {
                boolean exists = true;
                if (! Files.exists (runtimeLib))
                {
                    exists = false;
                }
                else if (factory.wrapperRequired ())
                {
                    String wrapperName = factory.prefixLibrary (shared) + runtimeName () + factory.suffixLibraryWrapper ();
                    Path wrapperLib = runtimeDir.resolve (wrapperName);
                    if (! Files.exists (wrapperLib)) exists = false;
                }

                if (! exists)
                {
                    job.set ("Linking runtime library", "status");
                    Compiler c = factory.compiler (localJobDir);
                    c.setShared ();
                    if (debug) c.setDebug ();
                    if (gprof) c.setProfiling ();
                    c.setOutput (runtimeLib);
                    addRuntimeObjects (c);
                    Path out = c.linkLibrary ();
                    Files.delete (out);
                }
            }

            runtimes.add (runtimeName);
        }
    }

    /**
        Places resources specific to this backend into runtimeDir.
        runtimeDir must be set before calling this function.
    **/
    public boolean unpackRuntime () throws Exception
    {
        job.set ("Unpacking runtime", "status");

        boolean changed = unpackRuntime
        (
            JobC.class, job, runtimeDir, "runtime/",
            "mymath.h", "fixedpoint.cc",
            "holder.cc", "holder.h", "holder.tcc",
            "KDTree.h", "mystring.h",
            "matrix.h", "Matrix.tcc", "MatrixFixed.tcc", "MatrixSparse.tcc", "pointer.h",
            "MNode.h", "MNode.cc",
            "nosys.h",
            "runtime.cc", "runtime.h", "runtime.tcc",
            "profiling.h", "profiling.cc",
            "myendian.h", "image.h", "Image.cc", "ImageFileFormat.cc", "ImageFileFormatBMP.cc", "PixelBuffer.cc", "PixelFormat.cc",
            "canvas.h", "CanvasImage.cc",
            "video.h", "Video.cc", "VideoFileFormatFFMPEG.cc",
            "NativeResource.cc", "NativeResource.h", "NativeIOvector.cc",
            "shared.h",
            "OutputHolder.h", "OutputParser.h",  // Not needed by runtime, but provided as a utility for users.
            "Shader.vp", "Shader.fp",  // GPU code not compiled into runtime.
            "glcorearb.h", "wglext.h"  // OpenGL headers provided by Khronos.
        );

        Path KHR = runtimeDir.resolve ("KHR");
        if (unpackRuntime (JobC.class, job, KHR, "runtime/KHR/", "khrplatform.h")) changed = true;

        return changed;
    }

    public static boolean unpackRuntime (Class<?> from, MNode job, Path runtimeDir, String prefix, String... names) throws Exception
    {
        // This code takes advantage of attribute caching in SshFileSystem
        // while remaining general for any NIO FileSystem.

        Map<String,Path> matches = new HashMap<String,Path> ();
        for (String s : names) matches.put (s, null);

        Files.createDirectories (runtimeDir);
        try (DirectoryStream<Path> dir = Files.newDirectoryStream (runtimeDir))
        {
            for (Path f : dir)
            {
                String s = f.getFileName ().toString ();
                if (matches.containsKey (s)) matches.put (s, f);
            }
        }

        boolean changed = false;
        for (Entry<String,Path> m : matches.entrySet ())
        {
            String s = m.getKey ();
            Path   f = m.getValue ();
            if (job != null) job.set ("Unpacking " + s, "status");

            long fileModified = 0;
            if (f == null) f = runtimeDir.resolve (s);
            else           fileModified = Files.getLastModifiedTime (f).toMillis ();

            URL url = from.getResource (prefix + s);
            long resourceModified = url.openConnection ().getLastModified ();
            if (resourceModified > fileModified)
            {
                changed = true;
                Files.copy (url.openStream (), f, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return changed;
    }

    /**
        Runtime object code files will be named source_type_featureA_featureB...
        in a standard order established by this function. 
    **/
    public String objectName (String stem)
    {
        StringBuilder result = new StringBuilder ();
        result.append (stem);
        result.append ("_" + T);
        if (shared) result.append ("_shared");
        if (debug ) result.append ("_debug");
        if (tls   ) result.append ("_tls");
        if (gprof ) result.append ("_gprof");
        result.append (".o");
        return result.toString ();
    }

    /**
        @return Name of the shared library that contains all runtime code.
        Does not include the suffix, because this is determined by context of the caller.
    **/
    public String runtimeName ()
    {
        StringBuilder result = new StringBuilder ();
        result.append ("runtime_" + T);
        if (debug) result.append ("_debug");
        if (tls  ) result.append ("_tls");
        if (gprof) result.append ("_gprof");
        return result.toString ();
    }

    public void addIncludes (Compiler c)
    {
        c.addInclude (runtimeDir);
        if (ffmpegIncDir != null)
        {
            c.addInclude (ffmpegIncDir);
            c.addDefine ("HAVE_FFMPEG");
        }
        if (jniIncMdDir != null  &&  shared  &&  ! (env instanceof Remote))
        {
            c.addInclude (jniIncMdDir);
            c.addInclude (jniIncDir);
            c.addDefine ("HAVE_JNI");
        }
        if (glLibs != null)
        {
            c.addDefine ("HAVE_GL");
        }
    }

    public void addRuntimeObjects (Compiler c) throws Exception
    {
        c.addObject (runtimeDir.resolve (objectName ("runtime")));
        c.addObject (runtimeDir.resolve (objectName ("holder")));
        c.addObject (runtimeDir.resolve (objectName ("MNode")));
        if (fixedPoint) c.addObject (runtimeDir.resolve (objectName ("fixedpoint")));
        c.addObject (runtimeDir.resolve (objectName ("CanvasImage")));
        c.addObject (runtimeDir.resolve (objectName ("Image")));
        c.addObject (runtimeDir.resolve (objectName ("ImageFileFormat")));
        c.addObject (runtimeDir.resolve (objectName ("ImageFileFormatBMP")));
        c.addObject (runtimeDir.resolve (objectName ("PixelBuffer")));
        c.addObject (runtimeDir.resolve (objectName ("PixelFormat")));
        if (ffmpegLibDir != null)
        {
            c.addObject (runtimeDir.resolve (objectName ("Video")));
            c.addObject (runtimeDir.resolve (objectName ("VideoFileFormatFFMPEG")));
            c.addLibraryDir (ffmpegLibDir);
            c.addLibrary ("avcodec");
            c.addLibrary ("avformat");
            c.addLibrary ("avutil");
        }
        if (lib  &&  jni  ||  jniIncMdDir != null  &&  shared  &&  ! (env instanceof Remote))
        {
            c.addObject (runtimeDir.resolve (objectName ("NativeResource")));
        }
        if (glLibs != null)
        {
            for (String lib : glLibs) c.addLibrary (lib);
            if (glLibDir != null) c.addLibraryDir (glLibDir);
        }

        for (ProvideOperator po : extensions)
        {
            Path path = po.library (this);
            if (path != null) c.addObject (path);
        }

        if (kokkos)
        {
            c.addObject (runtimeDir.resolve (objectName ("profiling")));
            c.addLibrary ("dl");  // kokkos should only be set on Linux systems.
        }
    }

    public Path build (Path source) throws Exception
    {
        job.set ("Compiling model", "status");

        CompilerFactory factory = BackendC.getFactory (env);
        String name   = source.getFileName ().toString ();
        int    pos    = name.lastIndexOf ('.');
        String stem   = pos > 0 ? name.substring (0, pos) : name;
        Path   binary = source.getParent ().resolve (stem + factory.suffixBinary ());

        Compiler c = factory.compiler (localJobDir);
        if (debug) c.setDebug ();
        if (gprof) c.setProfiling ();
        addIncludes (c);
        for (ProvideOperator po : extensions)
        {
            Path include = po.include (this);
            if (include == null) continue;
            Path dir = include.getParent ();
            if (dir != null) c.addInclude (dir);
        }
        c.addDefine ("n2a_T", T);
        if (fixedPoint) c.addDefine ("n2a_FP");
        if (tls) c.addDefine ("n2a_TLS");
        c.setOutput (binary);
        c.addSource (source);
        if (shared)
        {
            c.addLibraryDir (runtimeDir);
            c.addLibrary (runtimeName ());
            if (env instanceof Windows) c.addDefine ("n2a_DLL");
        }
        else
        {
            addRuntimeObjects (c);
        }

        Path out = c.compileLink ();
        Files.delete (out);

        return binary;
    }

    public void makeLibrary (Path source) throws Exception
    {
        // In order to make a library, we must compile in two steps:
        // first generate an object file, then link as library.
        CompilerFactory factory = BackendC.getFactory (env);
        Path parent  = source.getParent ();
        Path object  = parent.resolve (libStem + ".o");
        Path library = parent.resolve (factory.prefixLibrary (shared) + libStem + factory.suffixLibrary (shared));

        // 1) Generate object file
        Compiler c = factory.compiler (localJobDir);
        if (shared) c.setShared ();
        if (debug ) c.setDebug ();
        if (gprof ) c.setProfiling ();
        addIncludes (c);
        for (ProvideOperator po : extensions)
        {
            Path include = po.include (this);
            if (include == null) continue;
            Path dir = include.getParent ();
            if (dir != null) c.addInclude (dir);
        }
        c.addDefine ("n2a_T", T);
        if (fixedPoint) c.addDefine ("n2a_FP");
        if (tls) c.addDefine ("n2a_TLS");
        c.setOutput (object);
        c.addSource (source);
        Path out = c.compile ();
        Files.delete (out);

        // 2) Link library
        c.setOutput (library);
        c.addObject (object);
        addRuntimeObjects (c);
        out = c.linkLibrary ();
        Files.delete (out);
    }

    public void digestModel () throws Exception
    {
        job.set ("Analyzing model", "status");

        if (digestedModel.source.containsKey ("pin"))
        {
            digestedModel.collectPins ();
            digestedModel.fillAutoPins ();
            digestedModel.resolvePins ();
            digestedModel.purgePins ();
        }
        digestedModel.resolveConnectionBindings ();
        digestedModel.addGlobalConstants ();
        digestedModel.addSpecials ();  // $connect, $index, $init, $n, $t, $t'
        digestedModel.addAttribute ("global",      false, true,  "$max", "$min", "$k", "$radius");
        digestedModel.addAttribute ("global",      false, false, "$n");
        digestedModel.addAttribute ("state",       true,  false, "$n");  // Forbid $n from being temporary, even if it meets the criteria.
        digestedModel.addAttribute ("preexistent", true,  false, "$index", "$t");  // Technically, $index is not pre-existent, but always receives special handling which has the same effect.
        if (cli) tagCommandLineParameters (digestedModel, true);
        analyzeIOvectors (digestedModel);
        digestedModel.resolveLHS ();
        digestedModel.fillIntegratedVariables ();
        digestedModel.findIntegrated ();
        digestedModel.resolveRHS ();
        digestedModel.revertSingletonConnections ();
        digestedModel.flatten ("c");
        digestedModel.findExternal ();
        digestedModel.sortParts ();
        digestedModel.checkUnits ();
        digestedModel.findConstants ();
        digestedModel.determineTraceVariableName ();
        digestedModel.collectSplits ();
        digestedModel.findDeath ();  // Required by addImplicitDependencies(). When run before findInitOnly(), some parts may be marked lethalP when they don't need to be. One solution would be to run findDeath() again after findInitOnly().
        addImplicitDependencies (digestedModel);
        digestedModel.addDrawDependencies ();
        digestedModel.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        createBackendData (digestedModel);
        findPathToContainer (digestedModel);
        digestedModel.findAccountableConnections ();
        digestedModel.findTemporary ();  // for connections, makes $p and $project "temporary" under some circumstances.
        digestedModel.determineOrder ();
        digestedModel.findDerivative ();
        digestedModel.findInitOnly ();  // propagate initOnly through ASTs
        digestedModel.findDeath ();  // Re-run to ensure that lethalP is only set when necessary (see comment above).
        digestedModel.determinePoll ();
        digestedModel.purgeInitOnlyTemporary ();
        digestedModel.setAttributesLive ();
        digestedModel.forceTemporaryStorageForSpecials ();
        findLiveReferences (digestedModel);
        digestedModel.determineTypes ();
        digestedModel.determineDuration ();
        digestedModel.assignParents ();
        if (fixedPoint)
        {
            exponentContext = new ExponentContext (digestedModel);
            digestedModel.determineExponents (exponentContext);
        }
        digestedModel.findConnectionMatrix ();
        analyzeEvents (digestedModel);
        analyzeDt (digestedModel);
        analyze (digestedModel);
        analyzeNames (digestedModel);
    }

    public void tagCommandLineParameters (EquationSet s, boolean partCLI) throws IOException
    {
        MNode nodeCLI = s.metadata.child ("backend", "c", "cli");
        if (nodeCLI != null) partCLI = nodeCLI.getFlag ();

        for (Variable v : s.variables)
        {
            // Must be a simple constant tagged "param"
            if (v.metadata == null) continue;  // This can happen for $variables that are added at compile time.
            nodeCLI = v.metadata.child ("backend", "c", "cli");
            if (nodeCLI == null)  // Without CLI flag, base decision on param flag.
            {
                if (! partCLI) continue;
                if (! v.metadata.getFlag ("param")) continue;
            }
            else  // CLI flag takes precedence over everything else.
            {
                if (! nodeCLI.getFlag ()) continue;
            }
            if (v.equations.size () != 1) continue;
            EquationEntry e = v.equations.first ();
            if (e.condition != null) continue;
            // e must be a constant or init-only simple expression.
            // If it varies outside of init, those values will be ignored.
            v.addAttribute ("initOnly");  // prevents v from being eliminated by simplify
            v.addAttribute ("cli");  // private tag to remind us to generate CLI code for this variable

            String defaultValue = s.source.get (v.nameString ());

            // Determine parameter format/range hint
            String hint = v.metadata.get ("param");
            if (hint.trim ().equals ("1")) hint = "";  // Get rid of explicit positive case, since it isn't really a hint.
            if (hint.isBlank ()) hint = v.metadata.get ("study");
            hint = hint.trim ();

            String                  comment = v.metadata.get ("notes");
            if (comment.isBlank ()) comment = v.metadata.get ("note");
            if (comment.isBlank ()) comment = v.metadata.get ("description");
            int pos = comment.indexOf ('\n');
            if (pos >= 0) comment = comment.substring (0, pos);

            // Create parameter entry for this variable
            List<String> keypath = v.getKeyPath ();
            MVolatile node = (MVolatile) params.childOrCreate (keypath.toArray ());
            if (! defaultValue.isBlank ())
            {
                int length = defaultValue.length ();
                if (defaultValue.startsWith ("\"")  &&  defaultValue.endsWith ("\"")  &&  length >= 2)
                {
                    // The value is a string constant.
                    // This will be quoted by JSON, so we don't need the extra pair of quotes,
                    // and they may cause problems interpreting parameter inputs later.
                    defaultValue = defaultValue.substring (1, length - 1);
                }

                node.set (defaultValue, "default");
            }
            if (! comment.isBlank ()) node.set (comment, "description");
            if (! hint.isBlank ())
            {
                // Parse hint to determine GUI type
                if (hint.equals ("flag"))
                {
                    node.set ("flag", "type");
                }
                else if (hint.startsWith ("["))
                {
                    node.set ("number", "type");
                    hint = hint.substring (1);
                    String[] pieces = hint.split ("]", 2);
                    if (pieces.length > 1)
                    {
                        String unit = pieces[1].trim ();
                        if (! unit.isBlank ()) node.set (unit, "range", "unit");
                    }

                    pieces = pieces[0].split (",");
                    double lo = 0;
                    double hi = new UnitValue (pieces[0]).get ();
                    double step = 1;
                    if (pieces.length > 1)
                    {
                        lo = hi;
                        hi = new UnitValue (pieces[1]).get ();
                    }
                    if (pieces.length > 2)
                    {
                        step = new UnitValue (pieces[2]).get ();
                    }
                    if (lo   != 0) node.setObject (lo,   "range", "low");
                    if (hi   != 1) node.setObject (hi,   "range", "high");
                    if (step != 1) node.setObject (step, "range", "step");
                }
                else if (hint.contains (","))
                {
                    node.set ("choice", "type");
                    String[] pieces = hint.split (",");
                    for (int i = 0; i < pieces.length; i++) node.set (pieces[i].trim (), "choices", i);
                }
                // anything else --> plain text
            }
        }

        for (EquationSet p : s.parts) tagCommandLineParameters (p, partCLI);
    }

    /**
        Depends on the results of: addAttributes(), findDeath()
    **/
    public void addImplicitDependencies (EquationSet s)
    {
        if (fixedPoint)
        {
            // Force top-level model to keep $t', so it can retrieve time exponent.
            Variable dt = s.find (new Variable ("$t", 1));
            dt.addUser (s);
        }
        if (s.isSingleton ())
        {
            // Force singleton top-level model to keep $live, to signal when it dies.
            Variable live = s.find (new Variable ("$live"));
            live.addUser (s);
        }
        addImplicitDependenciesRecursive (s);
    }

    public void addImplicitDependenciesRecursive (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            addImplicitDependenciesRecursive (p);
        }
    
        final Variable dt    = s.find (new Variable ("$t", 1));
        Variable       index = s.find (new Variable ("$index"));
        Variable       p     = s.find (new Variable ("$p"));

        if (p != null)
        {
            if (s.lethalP) p.addDependencyOn (dt);  // $p gets normalized by $t' during probability draw
            if (p.metadata != null  &&  p.metadata.getDouble ("poll") > 0) p.addDependencyOn (index);  // polling uses $index for hash function
        }

        class VisitorDt implements Visitor
        {
            public Variable from;
            public boolean visit (Operator op)
            {
                if (op instanceof Input)
                {
                    Input i = (Input) op;
                    if (i.usesTime ()  &&  ! from.hasAttribute ("global")  &&  ! fixedPoint)
                    {
                        from.addDependencyOn (dt);  // So that time epsilon can be determined from dt when initializing input.
                    }
                }
                if (op instanceof Event)
                {
                    Event e = (Event) op;
                    if (e.operands.length > 1  &&  e.operands[1].getDouble () > 0)  // constant delay > 0
                    {
                        // We depend on $t' to know time exponent.
                        // This is necessary regardless of whether T=="int", because eventGenerate() handles this in a generic way.
                        EquationSet root = s.getRoot ();
                        Variable rootDt = root.find (dt);
                        rootDt.addUser (root);
                    }
                }
                return true;
            }
        }
        VisitorDt visitor = new VisitorDt ();
    
        for (Variable v : s.variables)
        {
            visitor.from = v;
            v.visit (visitor);
            if (v.derivative != null) v.addDependencyOn (dt);

            if (lib  &&  v.getMetadata ().getFlag ("backend", "c", "vector"))
            {
                EquationSet parent = s;
                while (parent != null)
                {
                    parent.needInstanceTracking = true;  // So it's possible to specify the exact population for the IO vector.
                    parent = parent.container;
                }
            }
        }

        // needInstanceTracking implies need $index
        if (s.needInstanceTracking  &&  ! s.isSingleton ())
        {
            index.addUser (s);
        }

        // Connection C with A.$k or A.$radius relies on A.$xyz, even if it does not define A.$project.
        // If C does define A.$project, then C depends on whatever $project uses. This will almost always
        // include A.$xyz.
        if (s.connectionBindings != null)
        {
            for (ConnectionBinding cb : s.connectionBindings)
            {
                String A = cb.alias;
                if (s.find (new Variable (A + ".$k")) != null  ||  s.find (new Variable (A + ".$radius")) != null)
                {
                    Variable xyz = cb.endpoint.find (new Variable ("$xyz"));
                    if (xyz != null) xyz.addUser (s);  // $xyz should never be null in this situation, but that's up to the user.
                }
            }
        }
    }

    public void createBackendData (EquationSet s)
    {
        if (! (s.backendData instanceof BackendDataC)) s.backendData = new BackendDataC ();
        for (EquationSet p : s.parts) createBackendData (p);
    }

    public void findPathToContainer (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            findPathToContainer (p);
        }

        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                if (c.endpoint.container == s.container)
                {
                    BackendDataC bed = (BackendDataC) s.backendData;
                    bed.pathToContainer = c.alias;
                    break;
                }
            }
        }
    }

    /**
        Finds all $live instances that we depend on to determine our own life/death.
        This information is used to limit garbage collection of dead parts until their
        dependent parts have had a chance to see the death and die themselves.
    **/
    public void findLiveReferences (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            findLiveReferences (p);
        }

        if (s.lethalConnection  ||  s.lethalContainer)
        {
            ArrayList<Object>         resolution     = new ArrayList<Object> ();
            NavigableSet<EquationSet> touched        = new TreeSet<EquationSet> ();
            if (! (s.backendData instanceof BackendDataC)) s.backendData = new BackendDataC ();
            findLiveReferences (s, resolution, touched, ((BackendDataC) s.backendData).localReference, false);
        }
    }

    /**
        Recursive subroutine of findLiveReferences() that walks up each possible containment or connection
        path to find $live values that can vary at runtime.
        This approach can potentially add entries in BackendDataC.localReference redundant with those added
        by BackendDataC.analyze(). Redundant entries are eliminated while emitting code in init() and die().
    **/
    @SuppressWarnings("unchecked")
    public void findLiveReferences (EquationSet s, ArrayList<Object> resolution, NavigableSet<EquationSet> touched, List<VariableReference> localReference, boolean terminate)
    {
        if (terminate)
        {
            Variable live = s.find (new Variable ("$live"));
            if (live == null  ||  live.hasAttribute ("constant")) return;

            // At this point, $live must be "initOnly".
            if (touched.add (s))  // Prevent us from adding the same part twice. However, does not prevent overlaps with those added by analyze().
            {
                VariableReference result = new VariableReference ();
                result.variable = live;
                result.resolution = (ArrayList<Object>) resolution.clone ();
                localReference.add (result);
                s.referenced = true;
            }
            return;
        }

        // Recurse up to container
        if (s.lethalContainer)
        {
            resolution.add (s.container);
            findLiveReferences (s.container, resolution, touched, localReference, true);
            resolution.remove (resolution.size () - 1);
        }

        // Recurse into connections
        if (s.lethalConnection)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                resolution.add (c);
                findLiveReferences (c.endpoint, resolution, touched, localReference, true);
                resolution.remove (resolution.size () - 1);
            }
        }
    }

    public void analyzeIOvectors (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            analyzeIOvectors (p);
            if (p.metadata.getFlag ("backend", "c", "vector")) s.metadata.set ("", "backend", "c", "vector", "part");
        }

        for (Variable v : s.variables)
        {
            if (v.getMetadata ().getFlag ("backend", "c", "vector")) s.metadata.set ("", "backend", "c", "vector", "variable");
        }
    }

    public void analyzeEvents (EquationSet s) throws Backend.AbortRun
    {
        BackendDataC bed = (BackendDataC) s.backendData;
        bed.analyzeEvents (s);
        for (EquationSet p : s.parts) analyzeEvents (p);
    }

    /**
        Determine how $t' and lastT are handled.
        Depends on the results of analyze() above.
        It is important that containers are processed before their children.
    **/
    public void analyzeDt (EquationSet s)
    {
        ((BackendDataC) s.backendData).analyzeDt (s);
        for (EquationSet p : s.parts) analyzeDt (p);
    }

    public void analyze (EquationSet s)
    {
        // It is important that children are processed before their container.
        for (EquationSet p : s.parts) analyze (p);

        BackendDataC bed = (BackendDataC) s.backendData;
        bed.analyze (s);

        Transformer t = new Transformer ()
        {
            public Operator transform (Operator op)
            {
                if (op instanceof Output)
                {
                    Output out = (Output) op;
                    // Special case for hosts that clobber stdout
                    // If first parameter is empty string, then replace with "out".
                    if (env.clobbersOut ()  &&  out.operands[0].toString ().isBlank ())
                    {
                        out.operands[0] = new Constant ("out");
                    }
                    return null;  // op is modified in place. We continue descent because output() can contain another output() function (though that wouldn't be a normal thing to do).
                }
                if (op instanceof MultiplyElementwise)
                {
                    MultiplyElementwise m = (MultiplyElementwise) op;
                    return new MultiplyElementwiseC (m);  // ctor does all the work of transferring operands to the new object.
                }
                return null;
            }
        };
        for (Variable v : s.variables) v.transform (t);
    }

    public void analyzeNames (EquationSet s)
    {
        // Determine if there is a name conflict.
        // Check source document for names, as it immediately covers all cases
        // (sub-part, variable, connection alias).
        // This approach still works for flattened parts, because anything brought
        // up from lower parts/ gets extended naming that should guarantee no conflicts
        // with part name.
        if (s.source.child (s.name) != null)
        {
            // Determine replacement name.
            String replacement;
            String prefix = AddPart.stripSuffix (s.name);
            int suffix = 2;
            while (true)
            {
                replacement = prefix + suffix;
                if (s.source.child (replacement) == null) break;
                suffix++;
            }

            // Apply replacement name.
            // We need to check all possible types of named objects within class.
            EquationSet p = s.findPart (s.name);
            if (p != null)
            {
                p.name = replacement;
            }
            else
            {
                ConnectionBinding c = s.findConnection (s.name);
                if (c != null)
                {
                    c.alias = replacement;
                }
                else
                {
                    Variable v = s.find (new Variable (s.name, -1));
                    if (v != null) v.name = replacement;
                }
            }
        }

        // Process sub-parts.
        // Notice that this includes any newly-renamed part.
        // Thus we fix any conflict created by the new name.
        for (EquationSet p : s.parts) analyzeNames (p);
    }

    public void generateCode (Path source) throws Exception
    {
        job.set ("Generating C++ code", "status");

        StringBuilder result = new StringBuilder ();
        RendererC context;
        if (fixedPoint) context = new RendererCfp (this, result);
        else            context = new RendererC   (this, result);
        BackendDataC bed = (BackendDataC) digestedModel.backendData;

        SIMULATOR = "Simulator<" + T + ">::instance" + (tls ? "->" : ".");

        result.append ("#include \"mymath.h\"\n");  // math.h must always come first, because it messes with mode in which <cmath> is included.
        result.append ("#include \"runtime.h\"\n");
        result.append ("#include \"MatrixFixed.tcc\"\n");  // Pulls in matrix.h, and thus access to all other matrix classes. We need templates for MatrixFixed because dimensions are arbitrary in user code.
        if (kokkos)
        {
            result.append ("#include \"profiling.h\"\n");
        }
        if (lib  &&  jni)
        {
            result.append ("#include \"NativeResource.h\"\n");
            result.append ("#include <jni.h>\n");
        }
        for (ProvideOperator po : extensions)
        {
            Path include = po.include (this);
            if (include == null) continue;
            result.append ("#include <" + include.getFileName () + ">\n");
        }
        result.append ("\n");
        result.append ("#include <stdlib.h>\n");
        result.append ("#include <iostream>\n");
        result.append ("#include <vector>\n");
        result.append ("#include <unordered_set>\n");  // Only needed for polling. We could walk the tree and check if any parts need polling before emitting this line, but it's probably not worth the effort.
        result.append ("#include <csignal>\n");
        if (env instanceof Windows)  // For hack to work around flaky batch processing. See main() below.
        {
            result.append ("#include <fstream>\n");
        }
        result.append ("\n");
        generateClassList (digestedModel, result);
        result.append ("class Wrapper;\n");
        result.append ("\n");
        assignNames (context, digestedModel);
        generateDeclarations (digestedModel, result);
        result.append ("class Wrapper : public WrapperBase<" + T + ">\n");
        result.append ("{\n");
        result.append ("public:\n");
        result.append ("  " + prefix (digestedModel) + "_Population " + mangle (digestedModel.name) + ";\n");
        result.append ("\n");
        result.append ("  Wrapper ()\n");
        result.append ("  {\n");
        result.append ("    population = &" + mangle (digestedModel.name) + ";\n");
        result.append ("    " + mangle (digestedModel.name) + ".container = this;\n");
        if (bed.singleton)
        {
            result.append ("    " + mangle (digestedModel.name) + ".instance.container = this;\n");
        }
        result.append ("  }\n");
        result.append ("};\n");
        if (tls) result.append ("thread_local ");
        result.append ("Wrapper * wrapper;\n");
        result.append ("\n");

        StringBuilder vectorDefinitions = new StringBuilder ();
        String SHARED = "";
        String ns = "";
        if (lib)
        {
            ns = "n2a";

            // Generate a companion header file
            String include = libStem + ".h";
            Path headerPath = source.getParent ().resolve (include);
            try (BufferedWriter header = Files.newBufferedWriter (headerPath))
            {
                String define = NodePart.validIdentifierFrom (libStem).replace (" ", "_") + "_h";
                header.append ("#ifndef " + define + "\n");
                header.append ("#define " + define + "\n");
                header.append ("\n");
                header.append ("#include <vector>\n");
                header.append ("#include <string>\n");
                header.append ("\n");

                if (shared)
                {
                    SHARED = "SHARED ";
                    // The following appears redundant with shared.h, but it's here for two reasons.
                    // 1) It simplifies the export for the user by not forcing them to obtain an extra include file.
                    // 2) The logic is slightly different. This always does dllimport when not building the dll,
                    //    whereas shared.h will only do it if n2a_DLL is defined. In other words, shared.h is really
                    //    only defined to work within the C runtime build system, while this block can work in
                    //    arbitrary user code.
                    // Note, however, that if the user grabs other resources like MNode, they still need to have
                    // shared.h and define n2a_DLL.
                    header.append ("#undef SHARED\n");
                    header.append ("#ifdef _MSC_VER\n");
                    header.append ("#  ifdef _USRDLL\n");
                    header.append ("#    define SHARED __declspec(dllexport)\n");
                    header.append ("#  else\n");
                    header.append ("#    define SHARED __declspec(dllimport)\n");
                    header.append ("#  endif\n");
                    header.append ("#else\n");
                    header.append ("#  define SHARED\n");
                    header.append ("#endif\n");
                    header.append ("\n");
                }

                header.append ("namespace " + ns + "\n");
                header.append ("{\n");
                if (jni)
                {
                    header.append ("  struct " + SHARED + "IOvector : public NativeResource\n");
                }
                else
                {
                    header.append ("  struct " + SHARED + "IOvector\n");
                }
                header.append ("  {\n");
                header.append ("    virtual int size () = 0;\n");
                header.append ("    virtual " + T + " get (int i) = 0;\n");
                header.append ("    virtual void set (int i, " + T + " value) = 0;\n");
                header.append ("  };\n");

                String ns_ = "";
                if (csharp)
                {
                    ns_ = ns + "_";
                    header.append ("}\n");
                    header.append ("\n");
                    header.append ("extern \"C\"\n");
                    header.append ("{\n");
                }
                else
                {
                    header.append ("\n");
                }

                header.append ("  " + SHARED + "void " + ns_ + "init (int argc, const char * argv[]);\n");
                header.append ("  " + SHARED + "void " + ns_ + "run (" + T + " until);\n");
                header.append ("  " + SHARED + "void " + ns_ + "finish ();\n");
                header.append ("  " + SHARED + "void " + ns_ + "releaseMemory ();\n");
                header.append ("\n");
                if (digestedModel.metadata.getFlag ("backend", "c", "vector"))
                {
                    generateIOvector (digestedModel, SHARED, ns, "  ", 0, vectorDefinitions);
                    if (csharp)
                    {
                        header.append ("  " + SHARED + ns + "::IOvector * " + ns_ + "IOvectorCreate (int keyCount, ...);  // All other args (keys) should be char *\n");
                        header.append ("  " + SHARED + "int   " + ns_ + "IOvectorSize   (" + ns + "::IOvector * self);\n");
                        header.append ("  " + SHARED + T +  " " + ns_ + "IOvectorGet    (" + ns + "::IOvector * self, int i);\n");
                        header.append ("  " + SHARED + "void  " + ns_ + "IOvectorSet    (" + ns + "::IOvector * self, int i, " + T + " value);\n");
                    }
                    else
                    {
                        // In this case, ns_ == "", so no need to include in expression.
                        // The following line is always in the n2a namespace.
                        header.append ("  " + SHARED + "IOvector * " + "IOvectorCreate (const std::vector<std::string> & keys);\n");
                    }
                    // If the client program uses a different C runtime than the shared library we generate,
                    // then a simple delete of the IOvector pointer in the client code will crash, because it will
                    // attempt to return the memory to a heap different from whence it came. A function in our
                    // own library will be linked to the correct runtime and thus send it back to the correct heap.
                    // That's why this function is always defined.
                    header.append ("  " + SHARED + "void  " + ns_ + "IOvectorDelete (" + ns + "::IOvector * self);\n");
                }

                if (csharp)
                {
                    header.append ("}\n");
                    header.append ("\n");
                    header.append ("namespace " + ns + "\n");
                    header.append ("{\n");
                }
                else
                {
                    header.append ("\n");
                }

                // Convenience wrappers for init()
                header.append ("  inline void initVector (const std::vector<std::string> & args)\n");
                header.append ("  {\n");
                header.append ("    int argc = args.size () + 1;\n");
                header.append ("    const char ** argv = (const char **) malloc (sizeof (char *) * argc);\n");
                header.append ("    for (int i = 1; i < argc; i++) argv[i] = args[i-1].c_str ();\n");
                header.append ("    " + ns_ + "init (argc, argv);\n");
                header.append ("    free (argv);\n");
                header.append ("  }\n");
                header.append ("  template<typename... Args> void initVector (Args... keys) {initVector ({keys...});}\n");
                header.append ("}\n");
                header.append ("\n");

                header.append ("#endif\n");
            }

            result.append ("#include \"" + include + "\"\n");
            result.append ("\n");
        }

        result.append ("using namespace n2a;\n");
        result.append ("using namespace std;\n");
        result.append ("\n");
        if (cli)
        {
            if (tls) result.append ("thread_local ");
            result.append ("Parameters<" + T + "> * params;\n");
        }
        generateStatic (context);
        result.append ("\n");
        generateDefinitions (context, digestedModel);

        // Init IO
        // This function is used both during initial startup and also when launching worker threads.
        // In the latter case, calls to getHolder() should simply fill in an existing handle.
        // The initializers may do some redundant work in that case, but shouldn't do any harm.
        result.append ("void initIO ()\n");
        result.append ("{\n");
        generateMainInitializers (context);
        result.append ("}\n");
        result.append ("\n");

        // Init
        String ns_ = ns;
        if (lib)
        {
            if (csharp) ns_ += "_";
            else        ns_ += "::";
        }
        result.append ("void " + ns_ + "init (int argc, const char * argv[])\n");
        result.append ("{\n");
        if (kokkos)
        {
            result.append ("  get_callbacks ();\n");
        }
        if (cli)
        {
            result.append ("  params = new Parameters<" + T + ">;\n");
            result.append ("  params->parse (argc, argv);\n");
        }
        if (fixedPoint)
        {
            Variable dt = digestedModel.find (new Variable ("$t", 1));
            result.append ("  Event<int>::exponent = " + dt.exponent + ";\n");
        }
        String integrator = digestedModel.metadata.getOrDefault ("Euler", "backend", "all", "integrator");
        if (integrator.equalsIgnoreCase ("RungeKutta")) integrator = "RungeKutta";
        else                                            integrator = "Euler";
        if (tls)
        {
            result.append ("  Simulator<" + T + ">::instance = new Simulator<" + T + ">;\n");
        }
        result.append ("  " + SIMULATOR + "integrator = new " + integrator + "<" + T + ">;\n");
        result.append ("  " + SIMULATOR + "after = " + after + ";\n");
        result.append ("  putenv ((char *) \"TZ=\");\n");  // Per tzset() manpage, setting TZ to blank causes us to be in UTC.
        result.append ("  initIO ();\n");
        result.append ("  wrapper = new Wrapper;\n");
        result.append ("  " + SIMULATOR + "init (wrapper);\n");  // Simulator takes possession of wrapper, so it will be freed automatically.
        result.append ("}\n");
        result.append ("\n");

        // Finish
        result.append ("void " + ns_ + "finish ()\n");
        result.append ("{\n");
        if (tls)
        {
            result.append ("  delete Simulator<" + T + ">::instance;\n");  // Calls Simulator::clear()
        }
        else
        {
            result.append ("  " + SIMULATOR + "clear ();\n");
        }
        //   Simulator::clear() ensures that all instances have been moved onto the dead list of their respective population.
        result.append ("  delete wrapper;\n");
        if (cli)
        {
            result.append ("  delete params;\n");
        }
        if (kokkos)
        {
            result.append ("  finalize_profiling ();\n");
        }
        result.append ("}\n");
        result.append ("\n");

        // Release Memory
        result.append ("void " + ns_ + "releaseMemory ()\n");
        result.append ("{\n");
        generateReleaseMemory (context, digestedModel);
        result.append ("  freeLUT ();\n");
        result.append ("  freeFormats ();\n");
        result.append ("}\n");
        result.append ("\n");

        // Main
        if (lib)
        {
            result.append ("void " + ns_ + "run (" + T + " until)\n");
            result.append ("{\n");
            result.append ("  " + SIMULATOR + "run (until);\n");
            result.append ("}\n");
            if (! vectorDefinitions.isEmpty ())
            {
                result.append ("\n");
                result.append (ns + "::IOvector * " + ns + "::IOvectorCreate (const std::vector<std::string> & keys)\n");
                result.append ("{\n");
                result.append ("  int keyCount = keys.size ();\n");
                result.append ("  if (keyCount == 0) return 0;\n");
                result.append ("  " + prefix (digestedModel) + "_Population & p0 = wrapper->" + mangle (digestedModel.name) + ";\n");
                result.append (vectorDefinitions.toString ());
                result.append ("  return 0;\n");
                result.append ("}\n");
                result.append ("\n");

                if (csharp)
                {
                    result.append ("#include <cstdarg>\n");
                    result.append (ns + "::IOvector * " + ns_ + "IOvectorCreate (int keyCount, ...)\n");
                    result.append ("{\n");
                    result.append ("  va_list args;\n");
                    result.append ("  va_start (args, keyCount);\n");
                    result.append ("  std::vector<std::string> keyPath (keyCount);\n");
                    result.append ("  for (int i = 0; i < keyCount; i++) keyPath[i] = va_arg (args, char *);\n");
                    result.append ("  va_end (args);\n");
                    result.append ("  return IOvectorCreate (keyPath);\n");
                    result.append ("}\n");

                    result.append ("int   " + ns_ + "IOvectorSize   (IOvector * self)                     {return self->size ();}\n");
                    result.append (T +  " " + ns_ + "IOvectorGet    (IOvector * self, int i)              {return self->get (i);}\n");
                    result.append ("void  " + ns_ + "IOvectorSet    (IOvector * self, int i, " + T + " value) {self->set (i, value);}\n");
                }
                result.append     ("void  " + ns_ + "IOvectorDelete (IOvector * self)                     {delete self;}\n");

                if (jni)
                {
                    result.append ("\n");
                    result.append ("#include \"NativeIOvector.cc\"\n");
                }
            }
        }
        else  // main() function for standalone simulator
        {
            result.append ("int main (int argc, const char * argv[])\n");
            result.append ("{\n");
            result.append ("  signal (SIGFPE,  signalHandler);\n");  // For error reporting
            result.append ("  signal (SIGSEGV, signalHandler);\n");  // ditto
            result.append ("  signal (SIGINT,  signalHandler);\n");  // For graceful shutdown
            result.append ("  signal (SIGTERM, signalHandler);\n");  // ditto
            result.append ("\n");
            result.append ("  srand (" + seed + ");\n");
            result.append ("  try\n");
            result.append ("  {\n");
            result.append ("    init (argc, argv);\n");
            result.append ("    " + SIMULATOR + "run ();\n");
            result.append ("    finish ();\n");  // Calls Simulator::clear(), which flushes output files.
            result.append ("    releaseMemory ();\n");  // Needed only to make memory checkers happy
            result.append ("\n");
            if (env instanceof Windows)  // Hack to work around flaky batch processing. See Windows.submitJob() for details.
            {
                result.append ("    ofstream ofs (\"finished\");\n");
                result.append ("    ofs << \"success\";\n");
            }
            result.append ("    return 0;\n");
            result.append ("  }\n");
            result.append ("  catch (const char * message)\n");
            result.append ("  {\n");
            result.append ("    cerr << \"Exception: \" << message << endl;\n");
            result.append ("  }\n");
            result.append ("  catch (...)\n");
            result.append ("  {\n");
            result.append ("    cerr << \"Generic Exception\" << endl;\n");
            result.append ("  }\n");
            if (env instanceof Windows)
            {
                result.append ("  ofstream ofs (\"finished\");\n");
                result.append ("  ofs << \"failure\";\n");
            }
            result.append ("  return 1;\n");
            result.append ("}\n");
        }

        Files.copy (new ByteArrayInputStream (result.toString ().getBytes ("UTF-8")), source);
    }

    public void generateClassList (EquationSet s, StringBuilder result)
    {
        for (EquationSet p : s.parts) generateClassList (p, result);
        result.append ("class " + prefix (s) + ";\n");
        result.append ("class " + prefix (s) + "_Population;\n");
    }

    public void assignNames (RendererC context, EquationSet s)
    {
        for (EquationSet p : s.parts) assignNames (context, p);

        context.setPart (s);
        BackendDataC bed = context.bed;

        class CheckStatic implements Visitor
        {
            public boolean visit (Operator op)
            {
                for (ProvideOperator po : extensions)
                {
                    Boolean result = po.assignNames (context, op);
                    if (result != null) return result;
                }
                if (op instanceof BuildMatrix)
                {
                    BuildMatrix m = (BuildMatrix) op;
                    m.name = "Matrix" + matrixNames.size ();
                    matrixNames.put (m, m.name);
                    return false;
                }
                if (op instanceof Constant)
                {
                    Constant constant = (Constant) op;
                    if (constant.value instanceof Matrix)
                    {
                        // Determine if matrix constant already exists. Don't create duplicates.
                        for (Constant c : staticMatrix)
                        {
                            Matrix A = (Matrix) constant.value;
                            Matrix B = (Matrix) c.value;
                            if (A.compareTo (B) != 0)
                            {
                                // Check for fixed-point matrices that produce the same int values.
                                int rows = A.rows ();
                                int cols = A.columns ();
                                if (! context.useExponent  ||  rows != B.rows ()  ||  cols != B.columns ()) continue;
                                boolean match = true;
                                Matrix R = A.divide (B);  // ratio between elements
                                double expectedRatio = Math.pow (2, constant.exponent - c.exponent);
                                for (int j = 0; match && j < cols; j++)
                                {
                                    for (int i = 0; match && i < rows; i++)
                                    {
                                        double ratio = R.get (i, j);
                                        if (Double.isNaN (ratio))  // probably due to divide by zero
                                        {
                                            if (A.get (i, j) != 0) match = false;
                                            continue;
                                        }
                                        if (ratio != expectedRatio) match = false;
                                    }
                                }
                                if (! match) continue;
                            }
                            constant.name = c.name;
                            matrixNames.put (constant, constant.name);
                            return false;
                        }

                        constant.name = "Matrix" + matrixNames.size ();
                        matrixNames.put (constant, constant.name);
                        staticMatrix.add (constant);
                    }
                    return true;
                }
                if (op instanceof Add  &&  op.parent instanceof Variable  &&  op.getType () instanceof Text)  // Users should avoid this in general, but can support computing names for IO functions in separate variables.
                {
                    Add a = (Add) op;
                    if (a.name == null)
                    {
                        Variable p = (Variable) a.parent;
                        if (p.equations.size () == 1  &&  p.equations.first ().condition == null)
                        {
                            a.name = mangle (p);
                        }
                        else
                        {
                            a.name = "string" + stringNames.size ();
                        }
                        stringNames.put (a, a.name);
                    }
                    // Every binary operator below this must be another Add.
                    // However, any operand that returns Text or Scalar is valid.
                    // Some of these could be IO functions. (But why? ...)
                    return true;
                }
                if (op instanceof Function)
                {
                    Function f = (Function) op;
                    if (f instanceof Output)  // Handle computed strings
                    {
                        Output o = (Output) f;
                        if (o.hasColumnName)
                        {
                            Operator operand2 = o.operands[2];
                            if (operand2 instanceof Add)
                            {
                                Add a = (Add) operand2;
                                a.name = "columnName" + stringNames.size ();
                                stringNames.put (a, a.name);
                            }
                        }
                        else  // We need to auto-generate the column name.
                        {
                            o.columnName = "columnName" + stringNames.size ();
                            stringNames.put (op, o.columnName);
                            if (context.global)
                            {
                                bed.setGlobalNeedPath (s);
                                bed.globalColumns.add (o.columnName);
                            }
                            else
                            {
                                bed.setLocalNeedPath  (s);
                                bed.localColumns.add (o.columnName);
                            }
                        }
                        if (o.keywords != null)
                        {
                            for (Operator kv : o.keywords.values ())
                            {
                                if (kv instanceof Add)  // Mode is calculated
                                {
                                    Add a = (Add) kv;
                                    a.name = "columnMode" + stringNames.size ();
                                    stringNames.put (a, a.name);
                                }
                            }
                        }
                    }
                    if (f instanceof Mfile) hasMfile = true;
                    // Detect functions that need static handles
                    if (f.operands.length > 0)
                    {
                        Operator operand0 = f.operands[0];
                        if (operand0 instanceof Constant)
                        {
                            Constant c = (Constant) operand0;
                            if (c.value instanceof Text)
                            {
                                String fileName = ((Text) c.value).value;
                                if (f instanceof ReadMatrix)
                                {
                                    ReadMatrix r = (ReadMatrix) f;
                                    r.name = matrixNames.get (fileName);
                                    if (r.name == null)
                                    {
                                        r.name = "Matrix" + matrixNames.size ();
                                        matrixNames.put (fileName, r.name);
                                        mainMatrix.add (r);
                                    }
                                }
                                else if (f instanceof Mfile)
                                {
                                    Mfile m = (Mfile) f;
                                    m.name = mfileNames.get (fileName);
                                    if (m.name == null)
                                    {
                                        m.name = "Mfile" + mfileNames.size ();
                                        mfileNames.put (fileName, m.name);
                                        mainMfile.add (m);
                                    }
                                }
                                else if (f instanceof Input)
                                {
                                    Input i = (Input) f;
                                    i.name = inputNames.get (fileName);
                                    if (i.name == null)
                                    {
                                        i.name = "Input" + inputNames.size ();
                                        inputNames.put (fileName, i.name);
                                        mainInput.add (i);
                                    }
                                }
                                else if (f instanceof Output)
                                {
                                    Output o = (Output) f;
                                    o.name = outputNames.get (fileName);
                                    if (o.name == null)
                                    {
                                        o.name = "Output" + outputNames.size ();
                                        outputNames.put (fileName, o.name);
                                        mainOutput.add (o);
                                    }
                                }
                                else if (f instanceof ReadImage)
                                {
                                    ReadImage r = (ReadImage) f;
                                    r.name = imageInputNames.get (fileName);
                                    if (r.name == null)
                                    {
                                        r.name = "Image" + imageInputNames.size ();
                                        imageInputNames.put (fileName, r.name);
                                        mainImageInput.add (r);
                                    }
                                }
                                else if (f instanceof Draw)
                                {
                                    Draw d = (Draw) f;
                                    d.name = imageOutputNames.get (fileName);
                                    if (d.name == null)
                                    {
                                        d.name = "Draw" + imageOutputNames.size ();
                                        imageOutputNames.put (fileName, d.name);
                                        mainImageOutput.add (d);
                                    }
                                }
                            }
                        }
                        else if (operand0 instanceof Add) // Dynamic file name (no static handle)
                        {
                            Add add = (Add) operand0;
                            if (f instanceof ReadMatrix)
                            {
                                ReadMatrix r = (ReadMatrix) f;
                                matrixNames.put (op,       r.name     = "Matrix"   + matrixNames.size ());
                                stringNames.put (operand0, r.fileName = "fileName" + stringNames.size ());
                                add.name = r.fileName;
                            }
                            else if (f instanceof Mfile)
                            {
                                Mfile m = (Mfile) f;
                                mfileNames .put (op,       m.name     = "Mfile"    + mfileNames .size ());
                                stringNames.put (operand0, m.fileName = "fileName" + stringNames.size ());
                                add.name = m.fileName;
                            }
                            else if (f instanceof Input)
                            {
                                Input i = (Input) f;
                                inputNames .put (op,       i.name     = "Input"    + inputNames .size ());
                                stringNames.put (operand0, i.fileName = "fileName" + stringNames.size ());
                                add.name = i.fileName;
                            }
                            else if (f instanceof Output)
                            {
                                Output o = (Output) f;
                                outputNames.put (op,       o.name     = "Output"   + outputNames.size ());
                                stringNames.put (operand0, o.fileName = "fileName" + stringNames.size ());
                                add.name = o.fileName;
                            }
                            else if (f instanceof ReadImage)
                            {
                                ReadImage r = (ReadImage) f;
                                imageInputNames.put (op,       r.name     = "Image"    + imageInputNames.size ());
                                stringNames    .put (operand0, r.fileName = "fileName" + stringNames.size ());
                                add.name = r.fileName;
                            }
                            else if (f instanceof Draw)
                            {
                                Draw d = (Draw) f;
                                imageOutputNames.put (op,       d.name     = "Draw"     + imageOutputNames.size ());
                                stringNames     .put (operand0, d.fileName = "fileName" + stringNames.size ());
                                add.name = d.fileName;
                            }
                        }
                        else if (operand0 instanceof AccessVariable)  // Dynamic file name in proper variable. Could be "initOnly".
                        {
                            AccessVariable av = (AccessVariable) operand0;
                            Variable v = av.reference.variable;
                            String fileName = resolve (av.reference, context, false);
                            if (f instanceof ReadMatrix)
                            {
                                ReadMatrix r = (ReadMatrix) f;
                                r.name = matrixNames.get (v);
                                if (r.name == null)
                                {
                                    r.name = "Matrix" + matrixNames.size ();
                                    matrixNames.put (v, r.name);
                                }
                                r.fileName = fileName;
                            }
                            else if (f instanceof Mfile)
                            {
                                Mfile m = (Mfile) f;
                                m.name = mfileNames.get (v);
                                if (m.name == null)
                                {
                                    m.name = "Mfile" + mfileNames.size ();
                                    mfileNames.put (v, m.name);
                                }
                                m.fileName = fileName;
                            }
                            else if (f instanceof Input)
                            {
                                Input i = (Input) f;
                                i.name = inputNames.get (v);
                                if (i.name == null)
                                {
                                    i.name = "Input" + inputNames.size ();
                                    inputNames.put (v, i.name);
                                }
                                i.fileName = fileName;
                            }
                            else if (f instanceof Output)
                            {
                                Output o = (Output) f;
                                o.name = outputNames.get (v);
                                if (o.name == null)
                                {
                                    o.name = "Output" + outputNames.size ();
                                    outputNames.put (v, o.name);
                                }
                                o.fileName = fileName;
                            }
                            else if (f instanceof ReadImage)
                            {
                                ReadImage r = (ReadImage) f;
                                r.name = imageInputNames.get (v);
                                if (r.name == null)
                                {
                                    r.name = "Image" + imageInputNames.size ();
                                    imageInputNames.put (v, r.name);
                                }
                                r.fileName = fileName;
                            }
                            else if (f instanceof Draw)
                            {
                                Draw d = (Draw) f;
                                d.name = imageOutputNames.get (v);
                                if (d.name == null)
                                {
                                    d.name = "Draw" + imageOutputNames.size ();
                                    imageOutputNames.put (v, d.name);
                                }
                                d.fileName = fileName;
                            }
                        }
                    }
                    return true;   // Functions could be nested, so continue descent.
                }
                return true;
            }
        }
        CheckStatic checkStatic = new CheckStatic ();
        for (Variable v : s.ordered)
        {
            context.global = v.hasAttribute ("global");
            v.visit (checkStatic);
        }
    }

    public void generateStatic (RendererC context)
    {
        StringBuilder result = context.result;
        String thread_local = tls ? "thread_local " : "";

        for (ProvideOperator po : extensions) po.generateStatic (context);
        for (Constant m : staticMatrix)
        {
            Matrix A = (Matrix) m.value;
            int rows = A.rows ();
            int cols = A.columns ();
            result.append ("MatrixFixed<" + T + "," + rows + "," + cols + "> " + m.name + " = {");
            String initializer = "";
            for (int c = 0; c < cols; c++)
            {
                for (int r = 0; r < rows; r++)
                {
                    initializer += context.print (A.get (r, c), m.exponent) + ", ";
                }
            }
            if (initializer.length () > 2) initializer = initializer.substring (0, initializer.length () - 2);
            result.append (initializer + "};\n");
        }
        for (ReadMatrix r : mainMatrix)
        {
            result.append (thread_local + "MatrixInput<" + T + "> * " + r.name + ";\n");
        }
        for (Mfile m : mainMfile)
        {
            result.append (thread_local + "Mfile<" + T + "> * " + m.name + ";\n");
        }
        for (Input i : mainInput)
        {
            result.append (thread_local + "InputHolder<" + T + "> * " + i.name + ";\n");
        }
        for (Output o : mainOutput)
        {
            result.append (thread_local + "OutputHolder<" + T + "> * " + o.name + ";\n");
        }
        for (ReadImage r : mainImageInput)
        {
            result.append (thread_local + "ImageInput<" + T + "> * " + r.name + ";\n");
        }
        for (Draw d : mainImageOutput)
        {
            result.append (thread_local + "ImageOutput<" + T + "> * " + d.name + ";\n");
        }
    }

    public void generateReleaseMemory (RendererC context, EquationSet s)
    {
        for (EquationSet p : s.parts) generateReleaseMemory (context, p);

        BackendDataC bed = (BackendDataC) s.backendData;
        if (bed.singleton) return;
        context.result.append ("  for (auto m : " + prefix (s) + "_Population::memory) delete[] m;\n");
    }

    public void generateMainInitializers (RendererC context)
    {
        StringBuilder result = context.result;
        for (ProvideOperator po : extensions) po.generateMainInitializers (context);
        for (ReadMatrix r : mainMatrix)
        {
            result.append ("  " + r.name + " = matrixHelper<" + T + "> (\"" + r.operands[0].getString () + "\"");
            if (fixedPoint) result.append (", " + r.exponent);
            result.append (");\n");
        }
        if (hasMfile)
        {
            result.append ("  MDoc::setMissingFileException (1);\n");  // Print warning.
        }
        for (Mfile m : mainMfile)
        {
            result.append ("  " + m.name + " = MfileHelper<" + T + "> (\"" + m.operands[0].getString () + "\");\n");
        }
        for (Input i : mainInput)
        {
            result.append ("  " + i.name + " = inputHelper<" + T + "> (\"" + i.operands[0].getString () + "\"");
            if (fixedPoint) result.append (", " + i.exponent + ", " + i.exponentRow);
            result.append (");\n");

            boolean smooth =             i.getKeywordFlag ("smooth");
            boolean time   = smooth  ||  i.getKeywordFlag ("time");
            if (time)   result.append ("  " + i.name + "->time = true;\n");
            if (smooth) result.append ("  " + i.name + "->smooth = true;\n");
            // TODO: need a way to know epsilon at this stage. Currently, we rely on individual part instances to set epsilon based on their own dt.
            // Several different instances may disagree on epsilon.
            // We could make a compile-time estimate of the smallest dt, and use dt/1000 just once here.
            // This is similar to the current approach for estimating time exponent for fixed-point.
        }
        for (Output o : mainOutput)
        {
            result.append ("  " + o.name + " = outputHelper<" + T + "> (\"" + o.operands[0].getString () + "\");\n");
            if (o.getKeywordFlag ("raw")) result.append ("  " + o.name + "->raw = true;\n");
        }
        for (ReadImage r : mainImageInput)
        {
            result.append ("  " + r.name + " = imageInputHelper<" + T + "> (\"" + r.operands[0].getString () + "\");\n");
        }
        for (Draw d : mainImageOutput)
        {
            result.append ("  " + d.name + " = imageOutputHelper<" + T + "> (\"" + d.operands[0].getString () + "\");\n");
        }
    }

    public void generateIOvector (EquationSet s, String SHARED, String ns, String pad, int keyPosition, StringBuilder result) throws IOException
    {
        BackendDataC bed = (BackendDataC) s.backendData;

        // Variables
        if (s.metadata.getFlag ("backend", "c", "vector", "variable"))
        {
            result.append (pad + "if (keyCount == " + (keyPosition + 1) + ")\n");  // On last position, so check variable name rather than population.
            result.append (pad + "{\n");
            for (Variable v : s.ordered)
            {
                if (! v.getMetadata ().getFlag ("backend", "c", "vector")) continue;
                result.append (pad + "  if (keys[" + keyPosition + "] == \"" + v.nameString () + "\")\n");
                result.append (pad + "  {\n");

                // Emit a class definition for this specific population and variable.
                String var = mangle (v.nameString ());
                String className = "IOvectorSpecific";
                result.append (pad + "    class " + className + ": public IOvector\n");
                result.append (pad + "    {\n");
                result.append (pad + "    public:\n");
                result.append (pad + "      " + prefix (s) + "_Population * population;\n");
                if (bed.singleton)
                {
                    result.append (pad + "      virtual int size ()\n");
                    result.append (pad + "      {\n");
                    result.append (pad + "        return 1;\n");
                    result.append (pad + "      }\n");
                    result.append (pad + "      virtual " + T + " get (int i)\n");
                    result.append (pad + "      {\n");
                    result.append (pad + "        return population->instance." + var + ";\n");
                    result.append (pad + "      }\n");
                    result.append (pad + "      virtual void set (int i, " + T + " value)\n");
                    result.append (pad + "      {\n");
                    result.append (pad + "        population->instance." + var + " = value;\n");
                    result.append (pad + "      }\n");
                }
                else
                {
                    result.append (pad + "      virtual int size ()\n");
                    result.append (pad + "      {\n");
                    result.append (pad + "        return population->instances.size ();\n");  // This assumes dense population. We won't try to guard against null entries.
                    result.append (pad + "      }\n");
                    result.append (pad + "      virtual " + T + " get (int i)\n");
                    result.append (pad + "      {\n");
                    result.append (pad + "        return population->instances.at (i)->" + var + ";\n");
                    result.append (pad + "      }\n");
                    result.append (pad + "      virtual void set (int i, " + T + " value)\n");
                    result.append (pad + "      {\n");
                    result.append (pad + "        population->instances.at (i)->" + var + " = value;\n");
                    result.append (pad + "      }\n");
                }
                result.append (pad + "    };\n");
                result.append ("\n");

                result.append (pad + "    " + className + " * result = new " + className + ";\n");
                result.append (pad + "    result->population = &p" + keyPosition + ";\n");
                result.append (pad + "    return result;\n");

                result.append (pad + "  }\n");
            }
            result.append (pad + "}\n");
        }

        // Parts
        if (s.metadata.getFlag ("backend", "c", "vector", "part"))
        {
            int nextPosition = keyPosition + (bed.singleton ? 1 : 2);
            result.append (pad + "if (keyCount > " + nextPosition + ")\n");  // Before last position, so process sub-populations
            result.append (pad + "{\n");
            if (bed.singleton)
            {
                result.append (pad + "  " + prefix (s) + " & instance = p" + keyPosition + ".instance;\n");
            }
            else
            {
                result.append (pad + "  int i = atoi (keys[" + keyPosition + "].c_str ());\n");
                result.append (pad + "  " + prefix (s) + " & instance = * p" + keyPosition + ".instances[i];\n");
            }
            result.append (pad + "  std::string partName = keys[" + (nextPosition - 1) + "];\n");
            String ifstring = "if";
            for (EquationSet p : s.parts)
            {
                if (! p.metadata.getFlag ("backend", "c", "vector")) continue;
                result.append (pad + "  " + ifstring + " (partName == \"" + p.name + "\")\n");
                result.append (pad + "  {\n");
                result.append (pad + "    " + prefix (p) + "_Population & p" + nextPosition + " = instance." + mangle (p.name) + ";\n");
                generateIOvector (p, SHARED, ns, pad + "    ", nextPosition, result);
                result.append (pad + "  }\n");
                ifstring = "else if";
            }
            result.append (pad + "}\n");
        }
    }

    /**
        Declares all classes, along with their member variables and functions.

        For each part, generates two classes: one for the instances ("local")
        and one for the population as a whole ("global"). Within each class,
        declares buffer classes for integration and derivation, then member
        variables, and finally member functions as appropriate.
    **/
    public void generateDeclarations (EquationSet s, StringBuilder result)
    {
        for (EquationSet p : s.parts) generateDeclarations (p, result);
        generateDeclarationsLocal (s, result);
        generateDeclarationsGlobal (s, result);
    }

    public void generateDeclarationsGlobal (EquationSet s, StringBuilder result)
    {
        BackendDataC bed = (BackendDataC) s.backendData;
        String prefix = prefix (s);

        // Templates for pollSorted
        // The use of a hash set to prevent duplicates is not the most time efficient.
        // A faster approach would keep a sorted list of existing connections,
        // perhaps with each neuron, then iterate over them and only test latent
        // connections when we find a gap in the sequence.
        // Regardless of approach, we will end up paying for extra storage.
        if (bed.poll >= 0)
        {
            result.append ("template <>\n");
            result.append ("struct std::hash<" + prefix + " *>\n");
            result.append ("{\n");
            result.append ("  size_t operator() (const " + prefix + " * a) const;\n");
            result.append ("};\n");
            result.append ("\n");

            result.append ("template <>\n");
            result.append ("struct std::equal_to<" + prefix + " *>\n");
            result.append ("{\n");
            result.append ("  bool operator() (const " + prefix + " * a, const " + prefix + " * b) const;\n");
            result.append ("};\n");
            result.append ("\n");
        }

        // Population class header
        result.append ("class " + prefix + "_Population : public Population<" + T + ">\n");
        result.append ("{\n");
        result.append ("public:\n");

        // Population buffers
        if (bed.needGlobalDerivative)
        {
            result.append ("  class Derivative\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            result.append ("    Derivative * next;\n");
            result.append ("  };\n");
            result.append ("\n");
        }
        if (bed.needGlobalPreserve)
        {
            result.append ("  class Preserve\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalDerivativePreserve)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("    " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            result.append ("  };\n");
            result.append ("\n");
        }

        // Population variables
        if (bed.singleton)
        {
            result.append ("  " + prefix + " instance;\n");
        }
        else
        {
            // Static members
            result.append ("  static std::vector<" + prefix + " *> dead;\n");
            result.append ("  static std::vector<" + prefix + " *> memory;\n");  // These are actually pointers to arrays rather than simple objects.
            result.append ("  static std::mutex mutexMemory;\n");
            result.append ("\n");

            if (bed.trackN)
            {
                result.append ("  uint32_t n;\n");
            }
            if (bed.trackInstances)
            {
                result.append ("  std::vector<" + prefix + " *> instances;\n");
                if (bed.index != null)
                {
                    // "instances" vector can supply next index.
                    result.append ("  uint32_t firstFreeIndex;\n");
                }
            }
            else
            {
                if (bed.index != null)
                {
                    // Without "instances" (above), we need another way to generate $index.
                    // This approach increments monotonically forever, without re-using old indices.
                    // Only suitable for small non-dynamic populations.
                    result.append ("  uint32_t nextIndex;\n");
                }
            }
            if (bed.newborn >= 0)
            {
                result.append ("  uint32_t firstborn;\n");
            }
        }
        if (bed.poll >= 0)
        {
            result.append ("  std::unordered_set<" + prefix + " *> pollSorted;\n");
            if (bed.poll > 0)
            {
                result.append ("  " + T + " pollDeadline;\n");  // Same type as Event::t
            }
        }
        if (bed.needGlobalDerivative)
        {
            result.append ("  Derivative * stackDerivative;\n");
        }
        if (bed.needGlobalPreserve)
        {
            result.append ("  Preserve * preserve;\n");
        }
        for (Variable v : bed.globalMembers)
        {
            result.append ("  " + type (v) + " " + mangle (v) + ";\n");
        }
        for (Variable v : bed.globalBufferedExternal)
        {
            result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
        }
        for (String columnName : bed.globalColumns)
        {
            result.append ("  String " + columnName + ";\n");
        }
        if (! bed.globalFlagType.isEmpty ())
        {
            // This should come last, because it can affect alignment.
            result.append ("  " + bed.globalFlagType + " flags;\n");
        }
        result.append ("\n");

        // Population functions
        if (bed.needGlobalCtor)
        {
            result.append ("  " + prefix + "_Population ();\n");
        }
        if (bed.needGlobalDtor)
        {
            result.append ("  virtual ~" + prefix + "_Population ();\n");
        }
        if (bed.needGlobalClear)
        {
            result.append ("  virtual void clear ();\n");
        }
        if (! bed.singleton)
        {
            result.append ("  virtual Part<" + T + "> * allocate ();\n");
            result.append ("  virtual void release (Part<" + T + "> * part);\n");
            if (bed.trackN  ||  bed.pathToContainer == null  ||  bed.trackInstances  ||  bed.index != null  ||  bed.poll >= 0)
            {
                result.append ("  virtual void add (Part<" + T + "> * part);\n");
            }
            if (bed.trackN  ||  bed.trackInstances  ||  bed.poll >= 0)
            {
                result.append ("  virtual void remove (Part<" + T + "> * part);\n");
            }
        }
        if (bed.needGlobalInit)
        {
            result.append ("  virtual void init ();\n");
        }
        if (bed.needGlobalIntegrate)
        {
            result.append ("  virtual void integrate ();\n");
        }
        if (bed.needGlobalUpdate)
        {
            result.append ("  virtual void update ();\n");
        }
        if (bed.needGlobalFinalize)
        {
            result.append ("  virtual int finalize ();\n");
        }
        if (bed.canResize)
        {
            result.append ("  virtual void resize (int n);\n");
        }
        if (bed.n != null  &&  ! bed.singleton)  // Slightly narrower condition than trackN.
        {
            result.append ("  virtual int getN ();\n");
        }
        if (bed.needGlobalUpdateDerivative)
        {
            result.append ("  virtual void updateDerivative ();\n");
        }
        if (bed.needGlobalFinalizeDerivative)
        {
            result.append ("  virtual void finalizeDerivative ();\n");
        }
        if (bed.needGlobalPreserve)
        {
            result.append ("  virtual void snapshot ();\n");
            result.append ("  virtual void restore ();\n");
        }
        if (bed.needGlobalDerivative)
        {
            result.append ("  virtual void pushDerivative ();\n");
            result.append ("  virtual void multiplyAddToStack (" + T + " scalar);\n");
            result.append ("  virtual void multiply (" + T + " scalar);\n");
            result.append ("  virtual void addToMembers ();\n");
        }
        if (bed.populationCanBeInactive  ||  bed.poll >= 0)
        {
            result.append ("  virtual void connect ();\n");
        }
        if (bed.newborn >= 0)
        {
            result.append ("  virtual void clearNew ();\n");
        }
        if (s.connectionBindings != null)
        {
            result.append ("  virtual ConnectIterator<" + T + "> * getIterators (bool poll);\n");
            result.append ("  virtual ConnectPopulation<" + T + "> * getIterator (int i, bool poll);\n");
        }
        if (bed.needGlobalPath)
        {
            result.append ("  virtual void path (String & result);\n");
        }

        // Population class trailer
        result.append ("};\n");
        result.append ("\n");
    }

    public void generateDeclarationsLocal (EquationSet s, StringBuilder result)
    {
        BackendDataC bed = (BackendDataC) s.backendData;

        // Unit class
        result.append ("class " + prefix (s) + " : public Part<" + T + ">\n");
        result.append ("{\n");
        result.append ("public:\n");

        // Unit buffers
        if (bed.needLocalDerivative)
        {
            result.append ("  class Derivative\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.localDerivative)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            result.append ("    Derivative * next;\n");
            result.append ("  };\n");
            result.append ("\n");
        }
        if (bed.needLocalPreserve)
        {
            result.append ("  class Preserve\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.localIntegrated)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.localDerivativePreserve)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.localBufferedExternalWriteDerivative)
            {
                result.append ("    " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            result.append ("  };\n");
            result.append ("\n");
        }

        // Unit variables
        if (bed.needLocalDerivative)
        {
            result.append ("  Derivative * stackDerivative;\n");
        }
        if (bed.needLocalPreserve)
        {
            result.append ("  Preserve * preserve;\n");
        }
        if (bed.pathToContainer == null)
        {
            result.append ("  " + prefix (s.container) + " * container;\n");
        }
        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                // we should be able to assume that s.container is non-null; ie: a connection should always operate in some larger container
                result.append ("  " + prefix (c.endpoint) + " * " + mangle (c.alias) + ";\n");
            }
        }
        if (s.accountableConnections != null)
        {
            for (EquationSet.AccountableConnection ac : s.accountableConnections)
            {
                result.append ("  uint32_t " + prefix (ac.connection) + "_" + mangle (ac.alias) + "_count;\n");
            }
        }
        if (bed.refcount)
        {
            result.append ("  uint32_t refcount;\n");
        }
        if (bed.index != null)
        {
            result.append ("  uint32_t " + mangle ("$index") + ";\n");
        }
        if (bed.lastT)
        {
            result.append ("  " + T + " lastT;\n");  // $lastT is for internal use only, so no need for __24 prefix.
        }
        for (Variable v : bed.localMembers)
        {
            result.append ("  " + type (v) + " " + mangle (v) + ";\n");
        }
        for (Variable v : bed.localBufferedExternal)
        {
            result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
        }
        for (EquationSet p : s.parts)
        {
            result.append ("  " + prefix (p) + "_Population " + mangle (p.name) + ";\n");
        }
        for (String columnName : bed.localColumns)
        {
            result.append ("  String " + columnName + ";\n");
        }
        for (EventSource es : bed.eventSources)
        {
            String eventMonitor = "eventMonitor_" + prefix (es.target.container);
            if (es.monitorIndex > 0) eventMonitor += "_" + es.monitorIndex;
            result.append ("  std::vector<Part<" + T + "> *> " + eventMonitor + ";\n");
        }
        for (EventTarget et : bed.eventTargets)
        {
            if (! et.trackOne  &&  et.edge != EventTarget.NONZERO)
            {
                result.append ("  " + T + " " + mangle (et.track.name) + ";\n");
            }
            if (et.timeIndex >= 0)
            {
                result.append ("  " + T + " eventTime" + et.timeIndex + ";\n");
            }
        }
        if (! bed.localFlagType.isEmpty ())
        {
            result.append ("  " + bed.localFlagType + " flags;\n");
        }
        int i = 0;
        for (Delay d : bed.delays)
        {
            d.index = i++;
            if (d.depth == 0)
            {
                result.append ("  DelayBuffer<" + T + "> delay" + d.index + ";\n");
            }
            else
            {
                result.append ("  RingBuffer<" + T + ", " + d.depth + "> delay" + d.index + ";\n");
            }
        }
        result.append ("\n");

        // Unit functions
        if (bed.needLocalCtor)
        {
            result.append ("  " + prefix (s) + " ();\n");
        }
        if (bed.needLocalDtor)
        {
            result.append ("  virtual ~" + prefix (s) + " ();\n");
        }
        if (bed.needLocalClear)
        {
            result.append ("  virtual void clear ();\n");
        }
        if (bed.needLocalDie)
        {
            result.append ("  virtual void die ();\n");
        }
        if (! bed.singleton)
        {
            result.append ("  virtual void remove ();\n");
        }
        if (bed.refcount)
        {
            result.append ("  virtual void ref ();\n");
            result.append ("  virtual void deref ();\n");
            result.append ("  virtual bool isFree ();\n");
        }
        if (bed.needLocalInit)
        {
            result.append ("  virtual void init ();\n");
        }
        if (bed.needLocalFlush)
        {
            if (bed.duplicate >= 0)
            {
                result.append ("  virtual void clearDuplicate ();\n");
            }
            result.append ("  virtual int flush ();\n");
        }
        if (bed.needLocalIntegrate)
        {
            result.append ("  virtual void integrate ();\n");
        }
        if (bed.needLocalUpdate)
        {
            result.append ("  virtual void update ();\n");
        }
        if (bed.needLocalFinalize)
        {
            result.append ("  virtual int finalize ();\n");
        }
        if (bed.needLocalUpdateDerivative)
        {
            result.append ("  virtual void updateDerivative ();\n");
        }
        if (bed.needLocalFinalizeDerivative)
        {
            result.append ("  virtual void finalizeDerivative ();\n");
        }
        if (bed.needLocalPreserve)
        {
            result.append ("  virtual void snapshot ();\n");
            result.append ("  virtual void restore ();\n");
        }
        if (bed.needLocalDerivative)
        {
            result.append ("  virtual void pushDerivative ();\n");
            result.append ("  virtual void multiplyAddToStack (" + T + " scalar);\n");
            result.append ("  virtual void multiply (" + T + " scalar);\n");
            result.append ("  virtual void addToMembers ();\n");
        }
        if (bed.connectionCanBeInactive)
        {
            result.append ("  void checkInactive ();\n");
        }
        result.append ("  virtual " + T + " getDt ();\n");
        if (bed.live != null  &&  ! bed.live.hasAttribute ("constant"))
        {
            result.append ("  virtual " + T + " getLive ();\n");
        }
        if (bed.xyz != null  &&  s.connected > 0)
        {
            result.append ("  virtual void getXYZ (MatrixFixed<" + T + ",3,1> & xyz);\n");
        }
        if (s.connectionBindings != null)
        {
            if (bed.p != null)
            {
                result.append ("  virtual " + T + " getP ();\n");
            }
            if (bed.hasProject)
            {
                result.append ("  virtual void getProject (int i, MatrixFixed<" + T + ",3,1> & xyz);\n");
            }
            result.append ("  virtual void setPart (int i, Part<" + T + "> * part);\n");
            result.append ("  virtual Part<" + T + "> * getPart (int i);\n");
        }
        if (bed.newborn >= 0)
        {
            result.append ("  virtual bool getNewborn ();\n");
        }
        if (s.connectionMatrix != null  &&  s.connectionMatrix.needsMapping)
        {
            result.append ("  virtual int mapIndex (int i, int rc);\n");
        }
        if (bed.eventTargets.size () > 0)
        {
            result.append ("  virtual bool eventTest (int i);\n");
            if (bed.needLocalEventDelay)
            {
                result.append ("  virtual " + T + " eventDelay (int i);\n");
            }
            result.append ("  virtual void setLatch (int i);\n");
            if (bed.eventReferences.size () > 0)
            {
                result.append ("  virtual void finalizeEvent ();\n");
            }
        }
        if (bed.accountableEndpoints.size () > 0)
        {
            result.append ("  virtual int getCount (int i);\n");
        }
        if (bed.needLocalPath)
        {
            result.append ("  virtual void path (String & result);\n");
        }

        // Conversions
        Set<Conversion> conversions = s.getConversions ();
        for (Conversion pair : conversions)
        {
            EquationSet source = pair.from;
            EquationSet dest   = pair.to;
            result.append ("  void Convert" + mangle (source.name) + mangle (dest.name) + " (" + prefix (source) + " * from, int " + mangle ("$type") + ");\n");
        }

        // Unit class trailer
        result.append ("};\n");
        result.append ("\n");
    }

    public void generateDefinitions (RendererC context, EquationSet s) throws Exception
    {
        for (EquationSet p : s.parts) generateDefinitions (context, p);

        context.setPart (s);
        generateDefinitionsLocal (context);
        generateDefinitionsGlobal (context);
    }

    public void generateDefinitionsGlobal (RendererC context) throws Exception
    {
        EquationSet   s      = context.part;
        BackendDataC  bed    = context.bed;
        StringBuilder result = context.result;
        context.global = true;
        String ps = prefix (s);
        String ns = ps + "_Population::";  // namespace for all functions associated with part s

        // Define static members in this translation unit.
        if (! bed.singleton)
        {
            result.append ("vector<" + ps + " *> " + ns + "dead;\n");
            result.append ("vector<" + ps + " *> " + ns + "memory;\n");
            result.append ("mutex " + ns + "mutexMemory;\n");
            result.append ("\n");
        }

        // Functions for pollSorted
        if (bed.poll >= 0)
        {
            result.append ("size_t\n");
            result.append ("std::hash<" + ps + " *>::operator() (const " + ps + " * a) const\n");
            result.append ("{\n");
            int count = s.connectionBindings.size ();
            if (count > 1)
            {
                result.append ("  const int shift = sizeof (size_t) * 8 / " + count + ";\n");
            }
            result.append     ("  size_t result =              a->" + mangle (s.connectionBindings.get (0).alias) + "->" + mangle ("$index") + ";\n");
            for (int i = 1; i < count; i++)
            {
                ConnectionBinding c = s.connectionBindings.get (i);
                result.append ("  result = (result << shift) + a->" + mangle (c.alias) + "->" + mangle ("$index") + ";\n");
            }
            result.append ("  return result;\n");
            result.append ("}\n");
            result.append ("\n");

            result.append ("bool\n");
            result.append ("std::equal_to<" + ps + " *>::operator() (const " + ps + " * a, const " + ps + " * b) const\n");
            result.append ("{\n");
            for (ConnectionBinding c : s.connectionBindings)
            {
                // Unlike the hash function, we don't bother looking up $index.
                // The endpoints must be exactly the same object to match.
                String alias = mangle (c.alias);
                result.append ("  if (a->" + alias + " != b->" + alias + ") return false;\n");
            }
            result.append ("  return true;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population ctor
        if (bed.needGlobalCtor)
        {
            result.append (ns + ps + "_Population ()\n");
            result.append ("{\n");
            if (bed.needGlobalDerivative)
            {
                result.append ("  stackDerivative = 0;\n");
            }
            if (bed.needGlobalPreserve)
            {
                result.append ("  preserve = 0;\n");
            }
            if (bed.needGlobalClear)
            {
                result.append ("  clear ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population dtor
        if (bed.needGlobalDtor)
        {
            result.append (ns + "~" + ps + "_Population ()\n");
            result.append ("{\n");
            if (bed.needGlobalDerivative)
            {
                result.append ("  while (stackDerivative)\n");
                result.append ("  {\n");
                result.append ("    Derivative * temp = stackDerivative;\n");
                result.append ("    stackDerivative = stackDerivative->next;\n");
                result.append ("    delete temp;\n");
                result.append ("  }\n");
            }
            if (bed.needGlobalPreserve)
            {
                result.append ("  if (preserve) delete preserve;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needGlobalClear)
        {
            result.append ("void " + ns + "clear ()\n");
            result.append ("{\n");
            if (! bed.singleton)
            {
                if (bed.trackN)
                {
                    result.append ("  n = 0;\n");
                }
                if (bed.trackInstances)
                {
                    if (bed.index != null)
                    {
                        result.append ("  firstFreeIndex = 0;\n");
                    }
                }
                else
                {
                    if (bed.index != null)
                    {
                        result.append ("  nextIndex = 0;\n");
                    }
                }
                if (bed.newborn >= 0)
                {
                    result.append ("  firstborn = 0;\n");
                }
            }
            if (! bed.globalFlagType.isEmpty ())
            {
                result.append ("  flags = 0;\n");
            }
            for (Variable v : bed.globalMembers)
            {
                if (v.hasAttribute ("MatrixPointer")) continue;
                result.append ("  " + zero (mangle (v), v) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population allocate / release / add / remove
        if (! bed.singleton)
        {
            result.append ("Part<" + T + "> * " + ns + "allocate ()\n");
            result.append ("{\n");
            result.append ("  Part<" + T + "> * result;\n");
            result.append ("  {\n");
            result.append ("    lock_guard<mutex> lock (mutexMemory);\n");
            result.append ("    int last = dead.size () - 1;\n");
            result.append ("    int i = last;\n");
            result.append ("    while (i >= 0  &&  ! dead[i]->isFree ()) i--;\n");  // Find the first free entry on dead list.
            result.append ("    if (i < 0)\n");  // No free entry, so allocate memory.
            result.append ("    {\n");
            result.append ("      int size = (1 << 20) / sizeof (" + ps + ");\n");  // As many instances as will fit in 1MiB. That is, allocation chunk is approximately 1 megabyte. TODO: get hint from metadata.
            result.append ("      " + ps + " * m = new " + ps + "[size];\n");
            result.append ("      memory.push_back (m);\n");
            result.append ("      " + ps + " * end = m + size;\n");
            result.append ("      while (m < end) dead.push_back (m++);\n");
            result.append ("      result = dead.back ();\n");
            result.append ("    }\n");
            result.append ("    else\n");
            result.append ("    {\n");
            result.append ("      result = dead[i];\n");
            result.append ("      if (i < last) dead[i] = dead[last];\n");  // remove from dead
            result.append ("    }\n");
            result.append ("    dead.pop_back ();\n");
            result.append ("  }\n");
            result.append ("  result->clear ();\n");
            result.append ("  return result;\n");
            result.append ("}\n");
            result.append ("\n");

            result.append ("void " + ns + "release (Part<" + T + "> * part)\n");
            result.append ("{\n");
            result.append ("  lock_guard<mutex> lock (mutexMemory);\n");
            result.append ("  dead.push_back ((" + ps + " *) part);\n");
            result.append ("}\n");
            result.append ("\n");

            // Population add / remove
            if (bed.trackN  ||  bed.pathToContainer == null  ||  bed.trackInstances  ||  bed.index != null  ||  bed.poll >= 0)
            {
                result.append ("void " + ns + "add (Part<" + T + "> * part)\n");
                result.append ("{\n");
                if (bed.trackN)
                {
                    result.append ("  n++;\n");
                }
                result.append ("  " + ps + " * p = (" + ps + " *) part;\n");
                if (bed.pathToContainer == null)
                {
                    result.append ("  p->container = (" + prefix (s.container) + " *) container;\n");
                }
                if (bed.trackInstances)
                {
                    result.append ("  uint32_t size = instances.size ();\n");
                    result.append ("  if (firstFreeIndex < size)\n");
                    result.append ("  {\n");
                    result.append ("    p->" + mangle ("$index") + " = firstFreeIndex++;\n");
                    result.append ("    instances[p->" + mangle ("$index") + "] = p;\n");
                    result.append ("    while (firstFreeIndex < size  &&  instances[firstFreeIndex]) firstFreeIndex++;\n");
                    result.append ("  }\n");
                    result.append ("  else\n");  // Presumably, firstFreeIndex is exactly instances.size()
                    result.append ("  {\n");
                    result.append ("    p->" + mangle ("$index") + " = size;\n");
                    result.append ("    instances.push_back (p);\n");
                    result.append ("    firstFreeIndex = size + 1;\n");
                    result.append ("  }\n");
                    if (bed.newborn >= 0)
                    {
                        result.append ("  firstborn = min (firstborn, p->" + mangle ("$index") + ");\n");
                    }
                }
                else
                {
                    if (bed.index != null)
                    {
                        result.append ("  p->" + mangle ("$index") + " = nextIndex++;\n");
                    }
                }
                if (bed.poll >= 0)
                {
                    result.append ("  pollSorted.insert (p);\n");
                }
                result.append ("}\n");
                result.append ("\n");
            }

            if (bed.trackN  ||  bed.trackInstances  ||  bed.poll >= 0)
            {
                result.append ("void " + ns + "remove (Part<" + T + "> * part)\n");
                result.append ("{\n");
                if (bed.trackN)
                {
                    result.append ("  n--;\n");
                }
                result.append ("  " + ps + " * p = (" + ps + " *) part;\n");
                if (bed.trackInstances)
                {
                    result.append ("  instances[p->" + mangle ("$index") + "] = 0;\n");
                    result.append ("  firstFreeIndex = min (firstFreeIndex, p->" + mangle ("$index") + ");\n");
                    result.append ("  release (part);\n");
                }
                if (bed.poll >= 0)
                {
                    result.append ("  pollSorted.erase (p);\n");
                }
                result.append ("}\n");
                result.append ("\n");
            }
        }

        // Population init
        if (bed.needGlobalInit)
        {
            result.append ("void " + ns + "init ()\n");
            result.append ("{\n");
            context.defined.clear ();
            s.setInit (1);

            // Zero out members
            for (Variable v : bed.globalBufferedExternalWrite)
            {
                // Clear current values so we can use a proper combiner during init.
                if (v.assignment == Variable.REPLACE) continue;
                result.append ("  " + clearAccumulator (mangle (v), v, context) + ";\n");
            }
            // Compute variables
            if (bed.nInitOnly)  // $n is not stored, so we need to declare a local variable to receive its value.
            {
                result.append ("  " + type (bed.n) + " " + mangle (bed.n) + ";\n");
            }
            s.simplify ("$init", bed.globalInit);
            if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, bed.globalInit);
            for (Variable v : bed.globalInit)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.globalBufferedExternalWrite)
            {
                if (v.assignment == Variable.REPLACE)
                {
                    result.append ("  " + mangle ("next_", v) + " = " + resolve (v.reference, context, false) + ";\n");
                }
                else
                {
                    result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
                }
            }

            // Create instances
            if (bed.singleton)
            {
                result.append ("  instance.init ();\n");
            }
            else
            {
                if (bed.n != null)  // and not singleton, so trackN is true
                {
                    result.append ("  resize (" + resolve (bed.n.reference, context, bed.nInitOnly));
                    if (context.useExponent) result.append (RendererC.printShift (bed.n.exponent));
                    result.append (");\n");
                }
            }

            // Make connections
            if (s.connectionBindings != null)
            {
                if (bed.poll > 0)
                {
                    // During the init cycle, a newly-created connection population will most likely examine
                    // all possible combinations of instances from target populations. Thus, it makes sense
                    // to wait one full period before polling.
                    result.append ("  pollDeadline = " + SIMULATOR + "currentEvent->t + ");
                    if (fixedPoint)
                    {
                        Variable dt = digestedModel.find (new Variable ("$t", 1));
                        result.append (context.print (bed.poll, dt.exponent));
                    }
                    else
                    {
                        result.append (bed.poll);
                    }
                    result.append (";\n");
                }
                result.append ("  " + SIMULATOR + "connect (this);\n");  // queue to evaluate our connections
            }
            s.setInit (0);
            result.append ("}\n");
            result.append ("\n");
        }

        // Population integrate
        if (bed.needGlobalIntegrate)
        {
            result.append ("void " + ns + "integrate ()\n");
            result.append ("{\n");
            if (kokkos) result.append ("  push_region (\"" + ns + "integrate()\");\n");

            if (s.container == null)
            {
                result.append ("  " + T + " dt = ((Wrapper *) container)->dt;\n");
            }
            else
            {
                BackendDataC cbed = (BackendDataC) s.container.backendData;
                if (cbed.lastT)
                {
                    result.append ("  " + T + " dt = " + SIMULATOR + "currentEvent->t - ((" + prefix (s.container) + " *) container)->lastT;\n");
                }
                else
                {
                    result.append ("  " + T + " dt = ((" + prefix (s.container) + " *) container)->getDt ();\n");
                }
            }

            result.append ("  if (preserve)\n");
            result.append ("  {\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + resolve (v.reference, context, false) + " = preserve->" + mangle (v) + " + ");
                // For fixed-point:
                // raw result = exponentDerivative+exponentTime
                // shift = raw-exponentVariable = exponentDerivative+exponentTime-exponentVariable
                int shift = v.derivative.exponent + bed.dt.exponent - v.exponent;
                if (shift != 0  &&  fixedPoint)
                {
                    result.append ("(int) ((int64_t) " + resolve (v.derivative.reference, context, false) + " * dt" + RendererC.printShift (shift) + ");\n");
                }
                else
                {
                    result.append (                      resolve (v.derivative.reference, context, false) + " * dt;\n");
                }
            }
            result.append ("  }\n");
            result.append ("  else\n");
            result.append ("  {\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + resolve (v.reference, context, false) + " += ");
                int shift = v.derivative.exponent + bed.dt.exponent - v.exponent;
                if (shift != 0  &&  fixedPoint)
                {
                    result.append ("(int) ((int64_t) " + resolve (v.derivative.reference, context, false) + " * dt" + RendererC.printShift (shift) + ");\n");
                }
                else
                {
                    result.append (                      resolve (v.derivative.reference, context, false) + " * dt;\n");
                }
            }
            result.append ("  }\n");
            if (kokkos) result.append ("  pop_region ();\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population update
        if (bed.needGlobalUpdate)
        {
            result.append ("void " + ns + "update ()\n");
            result.append ("{\n");
            context.defined.clear ();

            if (kokkos) result.append ("  push_region (\"" + ns + "update()\");\n");
            for (Variable v : bed.globalBufferedInternalUpdate)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            s.simplify ("$live", bed.globalUpdate);
            if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, bed.globalUpdate);
            for (Variable v : bed.globalUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.globalBufferedInternalUpdate)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            if (kokkos) result.append ("  pop_region ();\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population finalize
        if (bed.needGlobalFinalize)
        {
            result.append ("int " + ns + "finalize ()\n");
            result.append ("{\n");

            if (bed.canResize  &&  bed.n.derivative == null  &&  bed.canGrowOrDie)  // $n shares control with other specials, so must coordinate with them
            {
                // $n may be assigned during the regular update cycle, so we need to monitor it.
                result.append ("  if (" + mangle ("$n") + " != " + mangle ("next_", "$n") + ") " + SIMULATOR + "resize (this, max (0, " + mangle ("next_", "$n"));
                if (context.useExponent) result.append (RendererC.printShift (bed.n.exponent));
                result.append ("));\n");
                result.append ("  else " + SIMULATOR + "resize (this, -1);\n");
            }

            for (Variable v : bed.globalBufferedExternal)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWrite)
            {
                result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
            }

            if (s.connectionBindings != null)
            {
                if (bed.poll == 0)  // Always check connections every cycle.
                {
                    result.append ("  " + SIMULATOR + "connect (this);\n");
                }
                else  // Decide when to check connections based on poll deadline or status of endpoints.
                {
                    boolean needShouldConnect = bed.poll > 0;
                    for (ConnectionBinding target : s.connectionBindings)
                    {
                        BackendDataC bedc = (BackendDataC) target.endpoint.backendData;
                        if (bedc.canResize  ||  bedc.canGrow) needShouldConnect = true;
                    }

                    if (needShouldConnect)
                    {
                        result.append ("  bool shouldConnect = false;\n");

                        // Check poll deadline.
                        if (bed.poll > 0)
                        {
                            result.append ("  if (" + SIMULATOR + "currentEvent->t >= pollDeadline) shouldConnect = true;\n");
                        }

                        // Check for newly-created parts in endpoint populations.
                        Set<EquationSet> endpoints = new HashSet<EquationSet> ();
                        for (ConnectionBinding target : s.connectionBindings)
                        {
                            // Prevent duplicate code for connections to the same endpoint.
                            // Note that the check for new instances is only meaningful for distinct endpoints.
                            // The path to the endpoint doesn't matter.
                            if (endpoints.contains (target.endpoint)) continue;
                            endpoints.add (target.endpoint);

                            BackendDataC bedc = (BackendDataC) target.endpoint.backendData;
                            if (bedc.canResize  ||  bedc.canGrow) checkInstances (s, true, "", target.resolution, 0, "  ", result);
                        }

                        result.append ("  if (shouldConnect) " + SIMULATOR + "connect (this);\n");
                    }
                }
            }

            // Return value is ignored except for top-level population.
            boolean returnN = bed.needGlobalFinalizeN;
            if (bed.canResize)  
            {
                if (bed.canGrowOrDie)
                {
                    if (bed.n.derivative != null)  // $n' exists
                    {
                        // the rate of change in $n is pre-determined, so it relentlessly overrides any other structural dynamics
                        if (returnN)
                        {
                            result.append ("  if (n == 0) return 2;\n");
                            returnN = false;
                        }
                        result.append ("  " + SIMULATOR + "resize (this, max (0, " + mangle ("$n"));
                        if (context.useExponent) result.append (RendererC.printShift (bed.n.exponent));
                        result.append ("));\n");
                    }
                    // else case is handled above, at the start of finalize(), so it can compare $n to next_$n.
                }
                else  // $n is the only kind of structural dynamics, so simply do a resize() when needed
                {
                    if (returnN)
                    {
                        result.append ("  if (n == 0) return 2;\n");
                        returnN = false;
                    }
                    result.append ("  int floorN = max (0, ");
                    if (context.useExponent) result.append (mangle ("$n") + RendererC.printShift (bed.n.exponent));
                    else                     result.append ("(int) " + mangle ("$n"));
                    result.append (");\n");
                    result.append ("  if (n != floorN) " + SIMULATOR + "resize (this, floorN);\n");
                }
            }

            if (returnN)
            {
                // returnN can be true even if s is a singleton, because the top-level
                // part can have lethal $p but only create one instance. In that (very common)
                // case, $p controls lifespan of simulation. This also means we can't
                // use $n to indicate that the population is dead. The alternative is
                // to use $live.
                if (bed.singleton) result.append ("  return " + resolve (bed.live.reference, context, false, "instance.", true) + " ? 0 : 2;\n");
                else               result.append ("  return n > 0 ? 0 : 2;\n");
            }
            else
            {
                result.append ("  return 0;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population resize()
        if (bed.canResize)
        {
            result.append ("void " + ns + "resize (int n)\n");
            result.append ("{\n");
            if (bed.canGrowOrDie  &&  bed.n.derivative == null)
            {
                result.append ("  if (n < 0)\n");
                result.append ("  {\n");
                result.append ("    " + mangle ("$n") + " = this->n;\n");
                result.append ("    return;\n");
                result.append ("  }\n");
                result.append ("\n");
            }
            result.append ("  Population<" + T + ">::resize (n);\n");  // Grow population, if needed. Does not shrink population.
            result.append ("\n");
            result.append ("  for (int i = instances.size () - 1; this->n > n  &&  i >= 0; i--)\n");  // Shrink population, if needed.
            result.append ("  {\n");
            result.append ("    Part * p = instances[i];\n");
            result.append ("    if (p  &&  p->getLive ())\n");
            result.append ("    {\n");
            result.append ("      " + SIMULATOR + "linger (p->getDt ());\n");
            result.append ("      p->die ();\n");
            result.append ("      remove (p);\n");
            result.append ("    }\n");
            result.append ("  }\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population getN
        if (bed.n != null  &&  ! bed.singleton)
        {
            result.append ("int " + ns + "getN ()\n");
            result.append ("{\n");
            result.append ("  return n;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population updateDerivative
        if (bed.needGlobalUpdateDerivative)
        {
            result.append ("void " + ns + "updateDerivative ()\n");
            result.append ("{\n");
            context.defined.clear ();

            if (kokkos) result.append ("  push_region (\"" + ns + "updateDerivative()\");\n");
            for (Variable v : bed.globalBufferedInternalDerivative)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            s.simplify ("$live", bed.globalDerivativeUpdate);  // This is unlikely to make any difference. Just being thorough before call to multiconditional().
            if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, bed.globalDerivativeUpdate);
            for (Variable v : bed.globalDerivativeUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.globalBufferedInternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            if (kokkos) result.append ("  pop_region ();\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population finalizeDerivative
        if (bed.needGlobalFinalizeDerivative)
        {
            result.append ("void " + ns + "finalizeDerivative ()\n");
            result.append ("{\n");
            for (Variable v : bed.globalBufferedExternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needGlobalPreserve)
        {
            // Population snapshot
            result.append ("void " + ns + "snapshot ()\n");
            result.append ("{\n");
            result.append ("  preserve = new Preserve;\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalDerivativePreserve)
            {
                result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("  preserve->" + mangle ("next_", v) + " = " + mangle ("next_", v) + ";\n");
                result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Population restore
            result.append ("void " + ns + "restore ()\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivativePreserve)
            {
                result.append ("  " + mangle (v) + " = preserve->" + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("  " + mangle ("next_", v) + " = preserve->" + mangle ("next_", v) + ";\n");
            }
            result.append ("  delete preserve;\n");
            result.append ("  preserve = 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needGlobalDerivative)
        {
            // Population pushDerivative
            result.append ("void " + ns + "pushDerivative ()\n");
            result.append ("{\n");
            result.append ("  Derivative * temp = new Derivative;\n");
            result.append ("  temp->_next = stackDerivative;\n");
            result.append ("  stackDerivative = temp;\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  temp->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Population multiplyAddToStack
            result.append ("void " + ns + "multiplyAddToStack (" + T + " scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  stackDerivative->" + mangle (v) + " += ");
                if (fixedPoint)
                {
                    result.append ("(int) ((int64_t) " + mangle (v) + " * scalar >> " + (Operator.MSB - 1) + ");\n");
                }
                else
                {
                    result.append (                      mangle (v) + " * scalar;\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");

            // Population multiply
            result.append ("void " + ns + "multiply (" + T + " scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivative)
            {
                if (fixedPoint)
                {
                    result.append ("  " + mangle (v) + " = (int64_t) " + mangle (v) + " * scalar >> " + (Operator.MSB - 1) + ";\n");
                }
                else
                {
                    result.append ("  " + mangle (v) + " *= scalar;\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");

            // Population addToMembers
            result.append ("void " + ns + "addToMembers ()\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  " + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
            }
            result.append ("  Derivative * temp = stackDerivative;\n");
            result.append ("  stackDerivative = stackDerivative->next;\n");
            result.append ("  delete temp;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population connect (override for polling or inactive testing)
        if (bed.poll >= 0  ||  bed.populationCanBeInactive)
        {
            result.append ("void " + ns + "connect ()\n");
            result.append ("{\n");
            if (bed.poll < 0)
            {
                // Use default implementation of connect()
                result.append ("  Population::connect ();\n");
            }
            else  // poll >= 0
            {
                // Emit poll-aware code
                if (bed.poll > 0)
                {
                    result.append ("  bool poll = false;\n");
                    result.append ("  if (" + SIMULATOR + "currentEvent->t >= pollDeadline)\n");
                    result.append ("  {\n");
                    result.append ("    poll = true;\n");
                    result.append ("    pollDeadline = " + SIMULATOR + "currentEvent->t + ");
                    if (fixedPoint)
                    {
                        Variable dt = digestedModel.find (new Variable ("$t", 1));
                        result.append (context.print (bed.poll, dt.exponent));
                    }
                    else
                    {
                        result.append (bed.poll);
                    }
                    result.append (";\n");
                    result.append ("  }\n");
                }
                else  // poll == 0
                {
                    result.append ("  bool poll = true;\n");
                }
                result.append ("\n");
                result.append ("  ConnectIterator<" + T + "> * outer = getIterators (poll);\n");
                result.append ("  if (outer)\n");
                result.append ("  {\n");
                result.append ("    " + ps + " * c = (" + ps + " *) allocate ();\n");
                result.append ("    outer->setProbe (c);\n");
                result.append ("    while (outer->next ())\n");
                result.append ("    {\n");
                result.append ("      " + T + " p = c->getP ();\n");
                result.append ("      if (p <= 0) continue;\n");
                result.append ("      if (p < 1  &&  p < uniform<" + T + "> ()");
                if (fixedPoint) result.append (RendererC.printShift (-1 - Operator.MSB - bed.p.exponent));  // shift = exponentUniform - p.exponent = (-1-MSB) - p.exponent
                result.append (") continue;\n");
                result.append ("      if (poll  &&  pollSorted.count (c)) continue;\n");
                result.append ("\n");
                result.append ("      add (c);\n");
                result.append ("      c->init ();\n");
                result.append ("\n");
                result.append ("      c = (" + ps + " *) allocate ();\n");
                result.append ("      outer->setProbe (c);\n");
                result.append ("    }\n");
                result.append ("    release (c);\n");
                result.append ("    delete outer;\n");
                result.append ("  }\n");
                if (bed.populationCanBeInactive) result.append ("\n");
            }
            if (bed.populationCanBeInactive)
            {
                result.append ("  if (n == 0)\n");
                result.append ("  {\n");
                result.append ("    " + bed.setFlag ("flags", true, bed.inactive) + ";\n");
                result.append ("    ((" + prefix (s.container) + " *) container)->checkInactive ();\n");
                result.append ("  }\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population clearNew
        if (bed.newborn >= 0)
        {
            result.append ("void " + ns + "clearNew ()\n");
            result.append ("{\n");
            result.append ("  " + bed.clearFlag ("flags", true, bed.clearNew) + ";\n");  // Reset our clearNew flag
            if (bed.singleton)
            {
                result.append ("  " + bed.clearFlag ("instance.flags", false, bed.newborn) + ";\n");
            }
            else
            {
                result.append ("  int count = instances.size ();\n");
                result.append ("  for (int i = firstborn; i < count; i++)\n");
                result.append ("  {\n");
                result.append ("    " + ps + " * p = instances[i];\n");
                result.append ("    if (p) " + bed.clearFlag ("p->flags", false, bed.newborn) + ";\n");
                result.append ("  }\n");
                result.append ("  firstborn = count;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population getIterators
        if (s.connectionBindings != null)
        {
            class ConnectionHolder
            {
                public Operator      k;
                public Operator      min;
                public Operator      max;
                public Operator      radius;
                public boolean       hasProject;
                public EquationSet   endpoint;
                public List<Integer> indices = new ArrayList<Integer> ();
                public List<Object>  resolution;

                public boolean equivalent (Operator a, Operator b)
                {
                    if (a == b) return true;
                    if (a == null  ||  b == null) return false;
                    return a.equals (b);
                }

                public boolean equals (Object o)
                {
                    ConnectionHolder that = (ConnectionHolder) o;  // This is a safe assumption, since this is a local class.
                    return    equivalent (k,      that.k)
                           && equivalent (min,    that.min)
                           && equivalent (max,    that.max)
                           && equivalent (radius, that.radius)
                           && hasProject == that.hasProject
                           && endpoint   == that.endpoint;
                }

                public void emit ()
                {
                    for (Integer index : indices)
                    {
                        result.append ("    case " + index + ":\n");
                    }
                    result.append ("    {\n");
                    if (k == null  &&  radius == null)
                    {
                        result.append ("      result = new ConnectPopulation<" + T + "> (i, poll);\n");
                    }
                    else
                    {
                        result.append ("      result = new ConnectPopulationNN<" + T + "> (i, poll);\n");  // Pulls in KDTree dependencies, for full NN support.
                    }

                    boolean testK      = false;
                    boolean testRadius = false;
                    boolean constantKR = false;

                    if (k != null)
                    {
                        result.append ("      result->k = ");
                        k.render (context);
                        result.append (";\n");
                        testK = true;
                        if (k instanceof Constant)
                        {
                            Constant c = (Constant) k;
                            if (c.value instanceof Scalar  &&  ((Scalar) c.value).value != 0) constantKR = true;
                        }
                    }
                    if (max != null)
                    {
                        result.append ("      result->Max = ");
                        max.render (context);
                        result.append (";\n");
                    }
                    if (min != null)
                    {
                        result.append ("      result->Min = ");
                        min.render (context);
                        result.append (";\n");
                    }
                    if (radius != null)
                    {
                        result.append ("      result->radius = ");
                        radius.render (context);
                        result.append (";\n");
                        testRadius = true;
                        if (radius instanceof Constant)
                        {
                            Constant c = (Constant) radius;
                            if (c.value instanceof Scalar  &&  ((Scalar) c.value).value != 0) constantKR = true;
                        }
                    }
                    if (hasProject)
                    {
                        result.append ("      result->rank += 1;");
                    }
                    if (constantKR)
                    {
                        result.append ("      result->rank -= 2;\n");
                    }
                    else
                    {
                        if (testK  &&  testRadius)
                        {
                            result.append ("      if (result->k > 0  ||  result->radius > 0) result->rank -= 2;\n");
                        }
                        else if (testK)
                        {
                            result.append ("      if (result->k > 0) result->rank -= 2;\n");
                        }
                        else if (testRadius)
                        {
                            result.append ("      if (result->radius > 0) result->rank -= 2;\n");
                        }
                    }

                    assembleInstances (s, true, "", resolution, 0, "      ", result);
                    result.append ("      result->size = result->instances->size ();\n");

                    result.append ("      break;\n");
                    result.append ("    }\n");
                }
            }

            List<ConnectionHolder> connections = new ArrayList<ConnectionHolder> ();
            boolean needNN = false;  // TODO: Should determine this across the entire simulation, so that only one of getIteratorsSimple() or getIteratorsNN() is linked.
            for (ConnectionBinding c : s.connectionBindings)
            {
                ConnectionHolder h = new ConnectionHolder ();

                Variable v = s.find (new Variable (c.alias + ".$k"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null) h.k = e.expression;

                v = s.find (new Variable (c.alias + ".$max"));
                e = null;
                if (v != null) e = v.equations.first ();
                if (e != null) h.max = e.expression;

                v = s.find (new Variable (c.alias + ".$min"));
                e = null;
                if (v != null) e = v.equations.first ();
                if (e != null) h.min = e.expression;

                v = s.find (new Variable (c.alias + ".$radius"));
                e = null;
                if (v != null) e = v.equations.first ();
                if (e != null) h.radius = e.expression;

                h.hasProject = s.find (new Variable (c.alias + ".$project")) != null;
                h.endpoint = c.endpoint;

                int i = connections.indexOf (h);
                if (i < 0)
                {
                    connections.add (h);
                    h.resolution = c.resolution;
                }
                else
                {
                    h = connections.get (i);
                }
                h.indices.add (c.index);

                if (h.k != null  ||  h.radius != null) needNN = true;
            }


            result.append ("ConnectIterator<" + T + "> * " + ns + "getIterators (bool poll)\n");
            result.append ("{\n");
            if (s.connectionMatrix == null)
            {
                if (needNN)
                {
                    result.append ("  return getIteratorsNN (poll);\n");
                }
                else
                {
                    result.append ("  return getIteratorsSimple (poll);\n");
                }
            }
            else
            {
                ConnectionMatrix cm = s.connectionMatrix;
                result.append ("  ConnectPopulation<" + T + "> * rows = getIterator (" + cm.rows.index + ", poll);\n");  // TODO: does it make sense to pass "poll" rather than "false" here?
                result.append ("  ConnectPopulation<" + T + "> * cols = getIterator (" + cm.cols.index + ", poll);\n");
                result.append ("  if (rows->size == 0  ||  cols->size == 0)\n");
                result.append ("  {\n");
                result.append ("    delete rows;\n");
                result.append ("    delete cols;\n");
                result.append ("    return 0;\n");
                result.append ("  }\n");
                result.append ("  " + ps + " * dummy = (" + ps + " *) allocate ();\n");  // Will be deleted when ConnectMatrix is deleted.
                result.append ("  dummy->setPart (" + cm.rows.index + ", (*rows->instances)[0]);\n");
                result.append ("  dummy->setPart (" + cm.cols.index + ", (*cols->instances)[0]);\n");
                result.append ("  dummy->getP ();\n");  // We don't actually want $p. This just forces "dummy" to initialize any local matrix variables.

                // Create iterator
                result.append ("  IteratorNonzero<" + T + "> * it = ");
                boolean found = false;
                for (ProvideOperator po : extensions)
                {
                    if (po.getIterator (cm.A, context))
                    {
                        found = true;
                        break;
                    }
                }
                if (! found  &&  cm.A instanceof AccessElement)
                {
                    AccessElement ae = (AccessElement) cm.A;
                    Operator op0 = ae.operands[0];
                    result.append ("::getIterator (");
                    if (op0 instanceof AccessVariable)
                    {
                        AccessVariable av = (AccessVariable) op0;
                        Variable v = av.reference.variable;
                        if (v.hasAttribute ("temporary"))
                        {
                            // Just assume that v is an alias for ReadMatrix or Mmatrix.
                            // Also, matrix must be a static object. Enforced by AccessElement.hasCorrectForm().
                            // The following are variants of code in RendererC, but without pointer dereference.
                            Operator e = v.equations.first ().expression;
                            if (e instanceof ReadMatrix)
                            {
                                ReadMatrix r = (ReadMatrix) e;
                                result.append (r.name + "->A");
                            }
                            else if (e instanceof Mmatrix)
                            {
                                Mmatrix m = (Mmatrix) e;
                                result.append (m.name + "->getMatrix (");
                                result.append ("\"" + m.getDelimiter () + "\", ");
                                context.keyPath (m, 1);  // All the keys must be constant.
                                if (context.useExponent)
                                {
                                    if (m.operands.length > 1) result.append (", ");
                                    result.append (m.exponentNext);
                                }
                                result.append (")");
                            }
                            // TODO: else render with ProvideOperator
                        }
                        else
                        {
                            if (! v.hasAttribute ("MatrixPointer")) result.append ("& ");
                            context.global = false;
                            result.append (resolve (av.reference, context, true, "dummy->", false));  // Actually an rvalue, but we claim lvalue to finesse resolve() into not adding dereference for matrix pointer.
                            context.global = true;
                        }
                    }
                    else  // Must be a constant. Enforced by AccessElement.hasCorrectForm().
                    {
                        Constant c = (Constant) op0;
                        result.append (c.name);
                    }
                    result.append (");\n");
                }

                result.append ("  return new ConnectMatrix<" + T + "> (rows, cols, " + cm.rows.index + ", " + cm.cols.index + ", it, dummy, this);\n");
            }
            result.append ("}\n");
            result.append ("\n");


            result.append ("ConnectPopulation<" + T + "> * " + ns + "getIterator (int i, bool poll)\n");
            result.append ("{\n");
            result.append ("  ConnectPopulation<" + T + "> * result = 0;\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            for (ConnectionHolder h : connections) h.emit ();
            result.append ("  }\n");
            result.append ("  return result;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population path
        if (bed.needGlobalPath)
        {
            result.append ("void " + ns + "path (String & result)\n");
            result.append ("{\n");
            if (((BackendDataC) s.container.backendData).needLocalPath)  // Will our container provide a non-empty path?
            {
                result.append ("  container->path (result);\n");
                result.append ("  result += \"." + s.name + "\";\n");
            }
            else
            {
                result.append ("  result = \"" + s.name + "\";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }
    }

    public void generateDefinitionsLocal (RendererC context) throws Exception
    {
        EquationSet   s      = context.part;
        BackendDataC  bed    = context.bed;
        StringBuilder result = context.result;
        context.global = false;
        String ns = prefix (s) + "::";

        // Unit ctor
        if (bed.needLocalCtor)
        {
            result.append (ns + prefix (s) + " ()\n");
            result.append ("{\n");
            if (bed.needLocalDerivative)
            {
                result.append ("  stackDerivative = 0;\n");
            }
            if (bed.needLocalPreserve)
            {
                result.append ("  preserve = 0;\n");
            }
            for (EquationSet p : s.parts)
            {
                result.append ("  " + mangle (p.name) + ".container = this;\n");
                BackendDataC pbed = (BackendDataC) p.backendData;
                if (pbed.singleton)
                {
                    result.append ("  " + mangle (p.name) + ".instance.container = this;\n");
                }
            }
            if (bed.needLocalClear)
            {
                result.append ("  clear ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit dtor
        if (bed.needLocalDtor)
        {
            result.append (ns + "~" + prefix (s) + " ()\n");
            result.append ("{\n");
            if (bed.needLocalDerivative)
            {
                result.append ("  while (stackDerivative)\n");
                result.append ("  {\n");
                result.append ("    Derivative * temp = stackDerivative;\n");
                result.append ("    stackDerivative = stackDerivative->next;\n");
                result.append ("    delete temp;\n");
                result.append ("  }\n");
            }
            if (bed.needLocalPreserve)
            {
                result.append ("  if (preserve) delete preserve;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit clear
        if (bed.needLocalClear)
        {
            result.append ("void " + ns + "clear ()\n");
            result.append ("{\n");
            if (s.accountableConnections != null)
            {
                for (EquationSet.AccountableConnection ac : s.accountableConnections)
                {
                    result.append ("  " + prefix (ac.connection) + "_" + mangle (ac.alias) + "_count = 0;\n");
                }
            }
            if (bed.refcount)
            {
                result.append ("  refcount = 0;\n");
            }
            for (Variable v : bed.localMembers)
            {
                if (v.hasAttribute ("MatrixPointer")) continue;
                result.append ("  " + zero (mangle (v), v) + ";\n");
            }
            for (Delay d : bed.delays)
            {
                result.append ("  delay" + d.index + ".clear (");
                if (d.depth > 0)
                {
                    if (d.operands.length <= 2) result.append ("(" + T + ") 0");
                    else                        context.render (d.operands[2]);
                }
                result.append (");\n");
            }
            for (EquationSet p : s.parts)
            {
                BackendDataC pbed = (BackendDataC) p.backendData;
                if (pbed.needGlobalClear)
                {
                    result.append ("  " + mangle (p.name) + ".clear ();\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit die
        if (bed.needLocalDie)
        {
            result.append ("void " + ns + "die ()\n");
            result.append ("{\n");

            if (s.metadata.getFlag ("backend", "all", "fastExit"))
            {
                result.append ("  " + SIMULATOR + "stop = true;\n");
            }
            else
            {
                // tag part as dead
                if (bed.liveFlag >= 0)  // $live is stored in this part
                {
                    result.append ("  " + bed.clearFlag ("flags", false, bed.liveFlag) + ";\n");
                }

                // instance counting
                for (String alias : bed.accountableEndpoints)
                {
                    result.append ("  " + mangle (alias) + "->" + prefix (s) + "_" + mangle (alias) + "_count--;\n");
                }

                if (bed.localReference.size () > 0)
                {
                    // To prevent duplicates, we track which paths to containers have already been processed.
                    // The key is a string rather than EquationSet, because we may have references to several
                    // different instances of the same EquationSet, and all must be accounted.
                    TreeSet<String> touched = new TreeSet<String> ();
                    for (VariableReference r : bed.localReference)
                    {
                        String container = resolveContainer (r, context, "");
                        if (touched.add (container)) result.append ("  " + container + "refcount--;\n");
                    }
                }

                // release event monitors
                for (EventTarget et : bed.eventTargets)
                {
                    for (EventSource es : et.sources)
                    {
                        String part = "";
                        if (es.reference != null) part = resolveContainer (es.reference, context, "");
                        String eventMonitor = "eventMonitor_" + prefix (s);
                        if (es.monitorIndex > 0) eventMonitor += "_" + es.monitorIndex;
                        result.append ("  removeMonitor (" + part + eventMonitor + ", this);\n");
                    }
                }
            }

            result.append ("}\n");
            result.append ("\n");
        }

        // Unit remove
        if (! bed.singleton)
        {
            result.append ("void " + ns + "remove ()\n");
            result.append ("{\n");
            result.append ("  " + containerOf (s, false, "") + mangle (s.name) + ".remove (this);\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit ref / deref / isFree
        if (bed.refcount)
        {
            result.append ("void " + ns + "ref ()\n");
            result.append ("{\n");
            result.append ("  refcount++;\n");
            result.append ("}\n");
            result.append ("\n");

            result.append ("void " + ns + "deref ()\n");
            result.append ("{\n");
            result.append ("  refcount--;\n");
            result.append ("}\n");
            result.append ("\n");

            result.append ("bool " + ns + "isFree ()\n");
            result.append ("{\n");
            result.append ("  return refcount == 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit init
        if (bed.needLocalInit)
        {
            result.append ("void " + ns + "init ()\n");
            result.append ("{\n");
            context.defined.clear ();
            s.setInit (1);

            for (Variable v : bed.localBufferedExternalWrite)
            {
                // Clear current values so we can use a proper combiner during init.
                if (v.assignment == Variable.REPLACE) continue;
                result.append ("  " + clearAccumulator (mangle (v), v, context) + ";\n");
            }
            for (EventTarget et : bed.eventTargets)
            {
                if (et.timeIndex >= 0)
                {
                    result.append ("  eventTime" + et.timeIndex + " = 10;\n");  // Normal values are modulo 1 second. This initial value guarantees no match.
                }
                // Auxiliary variables get initialized as part of the regular list below.
            }
            if (! bed.localFlagType.isEmpty ())
            {
                long flags = 0;
                if (bed.liveFlag >= 0)
                {
                    // It's OK to set $live here, before the equations are executed, because
                    // the actual equations will have $live factored out by EquationSet.simplify() below.
                    flags |= 1 << bed.liveFlag;
                }
                if (bed.newborn >= 0)
                {
                    flags |= 1 << bed.newborn;
                }
                result.append ("  flags = " + flags + ";\n");
            }

            // Initialize static objects
            for (Variable v : bed.localInit)  // non-optimized list, so hopefully all variables are covered
            {
                for (EquationEntry e : v.equations)
                {
                    prepareStaticObjects (e.expression, context, "  ");
                    if (e.condition != null) prepareStaticObjects (e.condition, context, "  ");
                }
            }

            // Compute variables
            if (bed.lastT)
            {
                result.append ("  lastT = " + SIMULATOR + "currentEvent->t;\n");
            }
            if (bed.copyDt)
            {
                result.append ("  " + mangle (bed.dt) + " = container->getDt ();\n");
            }
            s.simplify ("$init", bed.localInit);
            if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, bed.localInit);
            for (Variable v : bed.localInit)  // optimized list: only variables with equations that actually fire during init
            {
                multiconditional (v, context, "  ");
            }
            if (bed.type != null)
            {
                result.append ("  " + mangle (bed.type) + " = 0;\n");
            }
            for (Variable v : bed.localBufferedExternalWrite)
            {
                if (v.assignment == Variable.REPLACE)
                {
                    result.append ("  " + mangle ("next_", v) + " = " + resolve (v.reference, context, false) + ";\n");
                }
                else
                {
                    result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
                }
            }
            String dt = resolve (bed.dt.reference, context, false);
            result.append ("  " + SIMULATOR + "enqueue (this, " + dt + ");\n");
            if (s.container == null)  // Top-level model, so move Wrapper as well.
            {
                result.append ("  if (container->dt != " + dt + ")\n");
                result.append ("  {\n");
                result.append ("    " + SIMULATOR + "linger (container->dt);\n");
                result.append ("    container->dt = " + dt + ";\n");
                result.append ("    " + SIMULATOR + "enqueue (container, " + dt + ");\n");
                result.append ("  }\n");
            }

            // instance counting
            for (String alias : bed.accountableEndpoints)
            {
                result.append ("  " + mangle (alias) + "->" + prefix (s) + "_" + mangle (alias) + "_count++;\n");
            }
            if (bed.localReference.size () > 0)
            {
                // TODO: "touched" repeats the effort already expended to generate die().
                TreeSet<String> touched = new TreeSet<String> ();
                for (VariableReference r : bed.localReference)
                {
                    String container = resolveContainer (r, context, "");
                    if (touched.add (container)) result.append ("  " + container + "refcount++;\n");
                }
            }

            // Request event monitors
            for (EventTarget et : bed.eventTargets)
            {
                for (EventSource es : et.sources)
                {
                    String part = "";
                    if (es.reference != null) part = resolveContainer (es.reference, context, "");
                    String eventMonitor = "eventMonitor_" + prefix (s);
                    if (es.monitorIndex > 0) eventMonitor += "_" + es.monitorIndex;
                    result.append ("  " + part + eventMonitor + ".push_back (this);\n");
                }
            }

            // contained populations
            if (s.orderedParts != null)
            {
                // If there are parts at all, then orderedParts must be filled in correctly. Otherwise it may be null.
                for (EquationSet e : s.orderedParts)
                {
                    if (((BackendDataC) e.backendData).needGlobalInit)
                    {
                        result.append ("  " + mangle (e.name) + ".init ();\n");
                    }
                }
            }

            s.setInit (0);
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needLocalFlush)
        {
            if (bed.duplicate >= 0)
            {
                result.append ("void " + ns + "clearDuplicate ()\n");
                result.append ("{\n");
                result.append ("  " + bed.clearFlag ("flags", false, bed.duplicate) + ";\n");
                result.append ("}\n");
                result.append ("\n");
            }

            result.append ("int " + ns + "flush ()\n");
            result.append ("{\n");

            if (bed.liveFlag >= 0)  // $live is stored in this part
            {
                result.append ("  if (! (" + bed.getFlag ("flags", false, bed.liveFlag) + ")) return 2;\n");
            }
            if (bed.dtCanChange)
            {
                result.append ("  if (" + mangle (bed.dt) + " != " + SIMULATOR + "currentEvent->dt) return 1;\n");
            }
            if (bed.duplicate >= 0)
            {
                result.append ("  if (" + bed.getFlag ("flags", false, bed.duplicate) + ") return 1;\n");
                result.append ("  " + bed.setFlag ("flags", false, bed.duplicate) + ";\n");
            }
            result.append ("  return 0;\n");

            result.append ("}\n");
            result.append ("\n");
        }

        // Unit integrate
        if (bed.needLocalIntegrate)
        {
            result.append ("void " + ns + "integrate ()\n");
            result.append ("{\n");
            if (kokkos) result.append ("  push_region (\"" + ns + "integrate()\");\n");

            if (bed.localIntegrated.size () > 0)
            {
                if (bed.lastT)
                {
                    result.append ("  " + T + " dt = " + SIMULATOR + "currentEvent->t - lastT;\n");
                }
                else
                {
                    result.append ("  " + T + " dt = " + resolve (bed.dt.reference, context, false) + ";\n");
                }
                // Note the resolve() call on the left-hand-side below has lvalue==false.
                // Integration always takes place in the primary storage of a variable.
                String pad = "";
                if (bed.needLocalPreserve)
                {
                    pad = "  ";
                    result.append ("  if (preserve)\n");
                    result.append ("  {\n");
                    for (Variable v : bed.localIntegrated)
                    {
                        result.append ("    " + resolve (v.reference, context, false) + " = preserve->" + mangle (v) + " + ");
                        int shift = v.derivative.exponent + bed.dt.exponent - v.exponent;
                        if (shift != 0  &&  fixedPoint)
                        {
                            if (v.type instanceof Matrix)
                            {
                                result.append ("::multiply (" + resolve (v.derivative.reference, context, false) + ", dt, " + -shift + ");\n");
                            }
                            else
                            {
                                result.append ("(int) ((int64_t) " + resolve (v.derivative.reference, context, false) + " * dt" + RendererC.printShift (shift) + ");\n");
                            }
                        }
                        else
                        {
                            result.append (resolve (v.derivative.reference, context, false) + " * dt;\n");
                        }
                    }
                    result.append ("  }\n");
                    result.append ("  else\n");
                    result.append ("  {\n");
                }
                for (Variable v : bed.localIntegrated)
                {
                    result.append (pad + "  " + resolve (v.reference, context, false) + " += ");
                    int shift = v.derivative.exponent + bed.dt.exponent - v.exponent;
                    if (shift != 0  &&  fixedPoint)
                    {
                        if (v.type instanceof Matrix)
                        {
                            result.append ("::multiply (" + resolve (v.derivative.reference, context, false) + ", dt, " + -shift + ");\n");
                        }
                        else
                        {
                            result.append ("(int) ((int64_t) " + resolve (v.derivative.reference, context, false) + " * dt" + RendererC.printShift (shift) + ");\n");
                        }
                    }
                    else
                    {
                        result.append (resolve (v.derivative.reference, context, false) + " * dt;\n");
                    }
                }
                if (bed.needLocalPreserve) result.append ("  }\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalIntegrate)
                {
                    result.append ("  " + mangle (e.name) + ".integrate ();\n");
                }
            }

            if (kokkos) result.append ("  pop_region ();\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit update
        if (bed.needLocalUpdate)
        {
            result.append ("void " + ns + "update ()\n");
            result.append ("{\n");
            context.defined.clear ();

            if (kokkos) result.append ("  push_region (\"" + ns + "update()\");\n");

            for (Variable v : bed.localBufferedInternalUpdate)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            s.simplify ("$live", bed.localUpdate);
            if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, bed.localUpdate);
            for (Variable v : bed.localUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.localBufferedInternalUpdate)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }

            // contained populations
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalUpdate)
                {
                    result.append ("  " + mangle (e.name) + ".update ();\n");
                }
            }

            if (kokkos) result.append ("  pop_region ();\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit finalize
        if (bed.needLocalFinalize)
        {
            result.append ("int " + ns + "finalize ()\n");
            result.append ("{\n");
            context.defined.clear ();

            // contained populations
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalFinalize)
                {
                    result.append ("  " + mangle (e.name) + ".finalize ();\n");  // ignore return value
                }
            }

            // Events
            for (EventSource es : bed.eventSources)
            {
                EventTarget et = es.target;
                String eventMonitor = "eventMonitor_" + prefix (et.container);
                if (es.monitorIndex > 0) eventMonitor += "_" + es.monitorIndex;

                if (es.testEach)
                {
                    result.append ("  for (Part * p : " + eventMonitor + ")\n");
                    result.append ("  {\n");
                    result.append ("    if (! p->eventTest (" + et.valueIndex + ")) continue;\n");
                    eventGenerate ("    ", et, context, false);
                    result.append ("  }\n");
                }
                else  // All monitors share same condition, so only test one.
                {
                    result.append ("  if (! " + eventMonitor + ".empty ()  &&  " + eventMonitor + "[0]->eventTest (" + et.valueIndex + "))\n");
                    result.append ("  {\n");
                    if (es.delayEach)  // Each target instance may require a different delay.
                    {
                        result.append ("    for (auto p : " + eventMonitor + ")\n");
                        result.append ("    {\n");
                        eventGenerate ("      ", et, context, false);
                        result.append ("    }\n");
                    }
                    else  // All delays are the same.
                    {
                        eventGenerate ("    ", et, context, true);
                    }
                    result.append ("  }\n");
                }
            }
            int eventCount = bed.eventTargets.size ();
            if (eventCount > 0)
            {
                result.append ("  flags &= ~(" + bed.localFlagType + ") 0" + RendererC.printShift (eventCount) + ";\n");
            }

            // Finalize variables
            if (bed.lastT)
            {
                result.append ("  lastT = " + SIMULATOR + "currentEvent->t;\n");
            }
            for (Variable v : bed.localBufferedExternal)
            {
                if (v == bed.dt)
                {
                    result.append ("  bool dtChanged =  " + mangle (v) + " != " + mangle ("next_", v) + ";\n");
                }
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.localBufferedExternalWrite)
            {
                if (v.assignment == Variable.REPLACE) continue;
                result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
            }

            if (! s.splits.isEmpty ())
            {
                result.append ("  switch ((int) " + mangle ("$type") + ")\n");
                result.append ("  {\n");
                // Each "split" is one particular set of new parts to transform into.
                // Each combination requires a separate piece of code. Thus, the outer
                // structure here is a switch statement. Each case within the switch implements
                // a particular combination of new parts. At this point, $type merely indicates
                // which combination to process. Afterward, it will be set to an index within that
                // combination, per the N2A language document.
                int countSplits = s.splits.size ();
                for (int i = 0; i < countSplits; i++)
                {
                    ArrayList<EquationSet> split = s.splits.get (i);

                    // Check if $type = me. Ignore this particular case, since it is a null operation
                    if (split.size () == 1  &&  split.get (0) == s)
                    {
                        continue;
                    }

                    result.append ("    case " + (i + 1) + ":\n");
                    result.append ("    {\n");
                    boolean used = false;  // indicates that this instance is one of the resulting parts
                    int countParts = split.size ();
                    for (int j = 0; j < countParts; j++)
                    {
                        EquationSet to = split.get (j);
                        if (to == s  &&  ! used)
                        {
                            used = true;
                            result.append ("      " + mangle ("$type") + " = " + (j + 1) + ";\n");
                        }
                        else
                        {
                            result.append ("      " + containerOf (s, false, "") + "Convert" + mangle (s.name) + mangle (to.name) + " (this, " + (j + 1) + ");\n");
                        }
                    }
                    if (used)
                    {
                        result.append ("      break;\n");
                    }
                    else
                    {
                        result.append ("      die ();\n");
                        result.append ("      return 2;\n");
                    }
                    result.append ("    }\n");
                }
                result.append ("  }\n");
            }

            if (s.lethalP)
            {
                // lethalP implies that $p exists, so no need to check for null
                if (bed.p.hasAttribute ("constant"))
                {
                    double pvalue = ((Scalar) ((Constant) bed.p.equations.first ().expression).value).value;
                    if (pvalue != 0)
                    {
                        // If $t' is exactly 1, then pow() is unnecessary here. However, that is a rare situation.
                        result.append ("  if (pow (" + resolve (bed.p.reference, context, false) + ", " + resolve (bed.dt.reference, context, false));
                        if (context.useExponent)
                        {
                            result.append (RendererC.printShift (bed.dt.exponent + Operator.MSB / 2));  // Second operand must have exponent=-MSB/2. shift = dt.exponent - -MSB/2
                            result.append (", " + bed.p.exponent);  // exponentA
                            result.append (", " + bed.p.exponent);  // exponentResult
                        }
                        result.append (") < uniform<" + T + "> ()");
                        if (context.useExponent) result.append (RendererC.printShift (-1 - Operator.MSB - bed.p.exponent));  // shift = exponentUniform - p.exponent = (-1-MSB) - p.exponent
                        result.append (")\n");
                    }
                }
                else
                {
                    if (bed.p.hasAttribute ("temporary"))
                    {
                        // Assemble a minimal set of expressions to evaluate $p
                        List<Variable> list = new ArrayList<Variable> ();
                        for (Variable t : s.ordered) if (t.hasAttribute ("temporary")  &&  bed.p.dependsOn (t) != null) list.add (t);
                        list.add (bed.p);
                        s.simplify ("$live", list, bed.p);
                        if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, list);
                        for (Variable v : list)
                        {
                            multiconditional (v, context, "  ");
                        }
                    }

                    result.append ("  if (" + mangle ("$p") + " <= 0  ||  " + mangle ("$p") + " < " + context.print (1, bed.p.exponent) + "  &&  pow (" + mangle ("$p") + ", " + resolve (bed.dt.reference, context, false));
                    if (context.useExponent)
                    {
                        result.append (RendererC.printShift (bed.dt.exponent + Operator.MSB / 2));
                        result.append (", " + bed.p.exponent);
                        result.append (", " + bed.p.exponent);
                    }
                    result.append (") < uniform<" + T + "> ()");
                    if (context.useExponent) result.append (RendererC.printShift (-1 - Operator.MSB - bed.p.exponent));
                    result.append (")\n");
                }
                result.append ("  {\n");
                result.append ("    die ();\n");
                result.append ("    return 2;\n");
                result.append ("  }\n");
            }

            if (s.lethalConnection)
            {
                for (ConnectionBinding c : s.connectionBindings)
                {
                	VariableReference r = s.resolveReference (c.alias + ".$live");
                	if (! r.variable.hasAttribute ("constant"))
                	{
                        result.append ("  if (" + resolve (r, context, false, "", true) + " == 0)\n");
                        result.append ("  {\n");
                        result.append ("    die ();\n");
                        result.append ("    return 2;\n");
                        result.append ("  }\n");
                	}
                }
            }

            if (s.lethalContainer)
            {
                VariableReference r = s.resolveReference ("$up.$live");
                if (! r.variable.hasAttribute ("constant"))
                {
                    result.append ("  if (" + resolve (r, context, false, "", true) + " == 0)\n");
                    result.append ("  {\n");
                    result.append ("    die ();\n");
                    result.append ("    return 2;\n");
                    result.append ("  }\n");
                }
            }

            if (bed.dt.hasAttribute ("externalWrite"))
            {
                result.append ("  if (dtChanged)\n");
                result.append ("  {\n");
                result.append ("    " + SIMULATOR + "enqueue (this, " + mangle (bed.dt) + ");\n");
                result.append ("    return 1;\n");
                result.append ("  }\n");
            }
            else
            {
                result.append ("  return 0;\n");
            }

            result.append ("}\n");
            result.append ("\n");
        }

        // Unit updateDerivative
        if (bed.needLocalUpdateDerivative)
        {
            result.append ("void " + ns + "updateDerivative ()\n");
            result.append ("{\n");
            context.defined.clear ();

            if (kokkos) result.append ("  push_region (\"" + ns + "updateDerivative()\");\n");
            for (Variable v : bed.localBufferedInternalDerivative)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            s.simplify ("$live", bed.localDerivativeUpdate);
            if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, bed.localDerivativeUpdate);
            for (Variable v : bed.localDerivativeUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.localBufferedInternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalUpdateDerivative)
                {
                    result.append ("  " + mangle (e.name) + ".updateDerivative ();\n");
                }
            }
            if (kokkos) result.append ("  pop_region ();\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit finalizeDerivative
        if (bed.needLocalFinalizeDerivative)
        {
            result.append ("void " + ns + "finalizeDerivative ()\n");
            result.append ("{\n");
            for (Variable v : bed.localBufferedExternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.localBufferedExternalWriteDerivative)
            {
                result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalFinalizeDerivative)
                {
                    result.append ("  " + mangle (e.name) + ".finalizeDerivative ();\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needLocalPreserve)
        {
            // Unit snapshot
            result.append ("void " + ns + "snapshot ()\n");
            result.append ("{\n");
            if (bed.needLocalPreserve)
            {
                result.append ("  preserve = new Preserve;\n");
                for (Variable v : bed.localIntegrated)
                {
                    result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
                for (Variable v : bed.localDerivativePreserve)
                {
                    result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
                for (Variable v : bed.localBufferedExternalWriteDerivative)
                {
                    result.append ("  preserve->" + mangle ("next_", v) + " = " + mangle ("next_", v) + ";\n");
                    result.append ("  " + clearAccumulator (mangle ("next_", v), v, context) + ";\n");
                }
            }
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalPreserve)
                {
                    result.append ("  " + mangle (e.name) + ".snapshot ();\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit restore
            result.append ("void " + ns + "restore ()\n");
            result.append ("{\n");
            if (bed.needLocalPreserve)
            {
                for (Variable v : bed.localDerivativePreserve)
                {
                    result.append ("  " + mangle (v) + " = preserve->" + mangle (v) + ";\n");
                }
                for (Variable v : bed.localBufferedExternalWriteDerivative)
                {
                    result.append ("  " + mangle ("next_", v) + " = preserve->" + mangle ("next_", v) + ";\n");
                }
                result.append ("  delete preserve;\n");
                result.append ("  preserve = 0;\n");
            }
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalPreserve)
                {
                    result.append ("  " + mangle (e.name) + ".restore ();\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needLocalDerivative)
        {
            // Unit pushDerivative
            result.append ("void " + ns + "pushDerivative ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                result.append ("  Derivative * temp = new Derivative;\n");
                result.append ("  temp->next = stackDerivative;\n");
                result.append ("  stackDerivative = temp;\n");
                for (Variable v : bed.localDerivative)
                {
                    result.append ("  temp->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
            }
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalDerivative)
                {
                    result.append ("  " + mangle (e.name) + ".pushDerivative ();\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit multiplyAddToStack
            result.append ("void " + ns + "multiplyAddToStack (" + T + " scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.localDerivative)
            {
                result.append ("  stackDerivative->" + mangle (v) + " += ");
                if (fixedPoint)
                {
                    result.append ("(int) ((int64_t) " + mangle (v) + " * scalar >> " + (Operator.MSB - 1) + ");\n");
                }
                else
                {
                    result.append (                      mangle (v) + " * scalar;\n");
                }
            }
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalDerivative)
                {
                    result.append ("  " + mangle (e.name) + ".multiplyAddToStack (scalar);\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit multiply
            result.append ("void " + ns + "multiply (" + T + " scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.localDerivative)
            {
                if (fixedPoint)
                {
                    result.append ("  " + mangle (v) + " = (int64_t) " + mangle (v) + " * scalar >> " + (Operator.MSB - 1) + ";\n");
                }
                else
                {
                    result.append ("  " + mangle (v) + " *= scalar;\n");
                }
            }
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalDerivative)
                {
                    result.append ("  " + mangle (e.name) + ".multiply (scalar);\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit addToMembers
            result.append ("void " + ns + "addToMembers ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                for (Variable v : bed.localDerivative)
                {
                    result.append ("  " + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
                }
                result.append ("  Derivative * temp = stackDerivative;\n");
                result.append ("  stackDerivative = stackDerivative->next;\n");
                result.append ("  delete temp;\n");
            }
            for (EquationSet e : s.parts)
            {
                if (((BackendDataC) e.backendData).needGlobalDerivative)
                {
                    result.append ("  " + mangle (e.name) + ".addToMembers ();\n");
                }
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit setPart
        if (s.connectionBindings != null)
        {
            result.append ("void " + ns + "setPart (int i, Part * part)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            for (ConnectionBinding c : s.connectionBindings)
            {
                result.append ("    case " + c.index + ": " + mangle (c.alias) + " = (" + prefix (c.endpoint) + " *) part; return;\n");
            }
            result.append ("  }\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getPart
        if (s.connectionBindings != null)
        {
            result.append ("Part<" + T + "> * " + ns + "getPart (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            for (ConnectionBinding c : s.connectionBindings)
            {
                result.append ("    case " + c.index + ": return " + mangle (c.alias) + ";\n");
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getCount
        if (bed.accountableEndpoints.size () > 0)
        {
            result.append ("int " + ns + "getCount (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            for (ConnectionBinding c : s.connectionBindings)
            {
                if (bed.accountableEndpoints.contains (c.alias))
                {
                    result.append ("    case " + c.index + ": return " + mangle (c.alias) + "->" + prefix (s) + "_" + mangle (c.alias) + "_count;\n");
                }
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getProject
        if (bed.hasProject)
        {
            result.append ("void " + ns + "getProject (int i, MatrixFixed<" + T + ",3,1> & xyz)\n");
            result.append ("{\n");
            context.defined.clear ();
            s.setConnect (1);

            // $project is evaluated similar to $p. The result is not stored.

            result.append ("  switch (i)\n");
            result.append ("  {\n");
            boolean needDefault = false;
            for (ConnectionBinding c : s.connectionBindings)
            {
                result.append ("    case " + c.index + ":");
                Variable project = s.find (new Variable (c.alias + ".$project"));
                if (project == null)  // fetch $xyz from endpoint
                {
                    VariableReference fromXYZ = s.resolveReference (c.alias + ".$xyz");
                    if (fromXYZ.variable == null)
                    {
                        needDefault = true;
                    }
                    else
                    {
                        if (fromXYZ.variable.hasAttribute ("temporary"))  // calculated value
                        {
                            result.append (" " + mangle (c.alias) + "->getXYZ (xyz); break;\n");
                        }
                        else  // stored value or "constant"
                        {
                            result.append (" xyz = " + resolve (fromXYZ, context, false) + "; break;\n");
                        }
                    }
                }
                else  // compute $project
                {
                    result.append ("\n");  // to complete the "case" line
                    result.append ("    {\n");
                    if (project.hasAttribute ("temporary"))  // it could also be "constant", but no other type
                    {
                        // Assemble a minimal set of expressions to evaluate $project
                        List<Variable> list = new ArrayList<Variable> ();
                        for (Variable t : s.ordered)
                        {
                            if ((t.hasAttribute ("temporary")  ||  bed.localMembers.contains (t))  &&  project.dependsOn (t) != null) list.add (t);
                        }
                        list.add (project);
                        s.simplify ("$connect", list, project);
                        if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, list);
                        for (Variable v : list)
                        {
                            multiconditional (v, context, "      ");
                        }
                    }
                    result.append ("      xyz = " + resolve (project.reference, context, false) + ";\n");
                    result.append ("      break;\n");
                    result.append ("    }\n");
                }
            }
            if (needDefault)
            {
                result.append ("    default:\n");
                result.append ("      xyz[0] = 0;\n");
                result.append ("      xyz[1] = 0;\n");
                result.append ("      xyz[2] = 0;\n");
            }
            result.append ("  }\n");

            s.setConnect (0);
            result.append ("}\n");
        }

        // Unit mapIndex
        if (s.connectionMatrix != null  &&  s.connectionMatrix.needsMapping)
        {
            result.append ("int " + ns + "mapIndex (int i, int rc)\n");
            result.append ("{\n");

            Variable rc = new Variable ("rc");
            rc.reference = new VariableReference ();
            rc.reference.variable = rc;
            rc.container = s;
            rc.addAttribute ("preexistent");
            AccessVariable av = new AccessVariable ();
            av.reference = rc.reference;

            ConnectionMatrix cm = s.connectionMatrix;
            cm.rowMapping.replaceRC (av);
            cm.colMapping.replaceRC (av);

            result.append ("  if (i == " + cm.rows.index + ") return ");
            cm.rowMapping.rhs.render (context);
            result.append (";\n");
            result.append ("  return ");
            cm.colMapping.rhs.render (context);
            result.append (";\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getNewborn
        if (bed.newborn >= 0)
        {
            result.append ("bool " + ns + "getNewborn ()\n");
            result.append ("{\n");
            result.append ("  return " + bed.getFlag ("flags", false, bed.newborn) + ";\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit checkInactive
        if (bed.connectionCanBeInactive)
        {
            result.append ("void " + ns + "checkInactive ()\n");
            result.append ("{\n");
            // Check each sub-population
            for (EquationSet e : s.parts)
            {
                BackendDataC ebed = (BackendDataC) e.backendData;
                result.append ("  if (! (" + bed.getFlag (mangle (e.name) + ".flags", true, ebed.inactive) + ")) return;\n");
            }
            // Remove inactive instance.
            result.append ("  " + SIMULATOR + "linger (getDt ());\n");
            result.append ("  die ();\n");
            result.append ("  remove ();\n");

            if (bed.populationCanBeInactive)
            {
                result.append ("  if (container->" + mangle (s.name) + ".n == 0)\n");
                result.append ("  {\n");
                result.append ("    " + bed.setFlag ("container->" + mangle (s.name) + ".flags", true, bed.inactive) + ";\n");
                result.append ("    container->checkInactive ();\n");
                result.append ("  }\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getDt
        result.append (T + " " + ns + "getDt ()\n");
        result.append ("{\n");
        if (bed.dt.hasAttribute ("accessor"))
        {
            if (s.container == null)
            {
                result.append ("  return container->getDt ();\n");  // Get default value from Wrapper.
            }
            else
            {
                // Notice that our container $t' won't be "constant", because otherwise we'd be "constant".
                // It could be an "initOnly" local variable, but not one that updates after init.
                BackendDataC pbed = (BackendDataC) s.container.backendData;
                context.part = s.container;
                result.append ("  return " + resolve (pbed.dt.reference, context, false, "container->", false) + ";\n");
                context.part = s;
            }
        }
        else  // "constant" or local variable (whether or not "initOnly")
        {
            result.append ("  return " + resolve (bed.dt.reference, context, false) + ";\n");
        }
        result.append ("}\n");
        result.append ("\n");

        // Unit getLive
        if (bed.live != null  &&  ! bed.live.hasAttribute ("constant"))
        {
            result.append (T + " " + ns + "getLive ()\n");
            result.append ("{\n");
            result.append ("  return " + resolve (bed.live.reference, context, false, "", true) + ";\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getP
        if (bed.p != null  &&  s.connectionBindings != null)  // Only connections need to provide an accessor
        {
            result.append (T + " " + ns + "getP ()\n");
            result.append ("{\n");
            context.defined.clear ();
            s.setConnect (1);

            if (! bed.p.hasAttribute ("constant"))
            {
                // Assemble a minimal set of expressions to evaluate $p
                List<Variable> list = new ArrayList<Variable> ();
                for (Variable t : s.ordered)
                {
                    if ((t.hasAttribute ("temporary")  ||  bed.localMembers.contains (t))  &&  bed.p.dependsOn (t) != null) list.add (t);
                }
                list.add (bed.p);
                s.simplify ("$connect", list, bed.p);
                if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, list);
                for (Variable v : list)
                {
                    multiconditional (v, context, "  ");
                }
            }
            result.append ("  return " + resolve (bed.p.reference, context, false) + ";\n");

            s.setConnect (0);
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getXYZ
        if (bed.xyz != null  &&  s.connected > 0)  // Connection targets need to provide an accessor.
        {
            result.append ("void " + ns + "getXYZ (MatrixFixed<" + T + ",3,1> & xyz)\n");
            result.append ("{\n");
            context.defined.clear ();

            // $xyz is either stored, "temporary", or "constant"
            // If "temporary", then we compute it on the spot.
            // If "constant", then we use the static matrix created during variable analysis
            // If stored, then simply copy into the return value.
            if (bed.xyz.hasAttribute ("temporary"))
            {
                // Assemble a minimal set of expressions to evaluate $xyz
                List<Variable> list = new ArrayList<Variable> ();
                for (Variable t : s.ordered) if (t.hasAttribute ("temporary")  &&  bed.xyz.dependsOn (t) != null) list.add (t);
                list.add (bed.xyz);
                s.simplify ("$live", list, bed.xyz);  // evaluate in $live phase, because endpoints already exist when connection is evaluated.
                if (fixedPoint) EquationSet.determineExponentsSimplified (exponentContext, list);
                for (Variable v : list)
                {
                    multiconditional (v, context, "  ");
                }
            }
            result.append ("  xyz = " + resolve (bed.xyz.reference, context, false) + ";\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit events
        if (bed.eventTargets.size () > 0)
        {
            result.append ("bool " + ns + "eventTest (int i)\n");
            result.append ("{\n");
            context.defined.clear ();

            result.append ("  switch (i)\n");
            result.append ("  {\n");
            for (EventTarget et : bed.eventTargets)
            {
                result.append ("    case " + et.valueIndex + ":\n");
                result.append ("    {\n");
                // Not safe or useful to simplify et.dependencies before emitting.
                for (Variable v : et.dependencies)
                {
                    multiconditional (v, context, "      ");
                }
                if (et.edge != EventTarget.NONZERO)
                {
                    result.append ("      " + T + " before = ");
                    if (et.trackOne) result.append (resolve (et.track.reference, context, false));
                    else             result.append (mangle (et.track.name));
                    result.append (";\n");
                }
                if (et.trackOne)  // This is a single variable, so check its value directly.
                {
                    result.append ("      " + T + " after = " + resolve (et.track.reference, context, true) + ";\n");
                }
                else  // This is an expression, so use our private auxiliary variable.
                {
                    result.append ("      " + T + " after = ");
                    et.event.operands[0].render (context);
                    result.append (";\n");
                    if (et.edge != EventTarget.NONZERO)
                    {
                        result.append ("      " + mangle (et.track.name) + " = after;\n");
                    }
                }
                switch (et.edge)
                {
                    case EventTarget.NONZERO:
                        if (et.timeIndex >= 0)
                        {
                            // Guard against multiple events in a given cycle.
                            // Note that other trigger types don't need this because they set the auxiliary variable,
                            // so the next test in the same cycle will no longer see change.
                            result.append ("      if (after == 0) return false;\n");
                            if (fixedPoint)
                            {
                                result.append ("      " + T + " moduloTime = " + SIMULATOR + "currentEvent->t;\n");  // No need for modulo arithmetic. Rather, int time should be wrapped elsewhere.
                            }
                            else  // float, double
                            {
                                result.append ("      " + T + " moduloTime = (" + T + ") fmod (" + SIMULATOR + "currentEvent->t, 1);\n");  // Wrap time at 1 second, to fit in float precision.
                            }
                            result.append ("      if (eventTime" + et.timeIndex + " == moduloTime) return false;\n");
                            result.append ("      eventTime" + et.timeIndex + " = moduloTime;\n");
                            result.append ("      return true;\n");
                        }
                        else
                        {
                            result.append ("      return after != 0;\n");
                        }
                        break;
                    case EventTarget.CHANGE:
                        result.append ("      return before != after;\n");
                        break;
                    case EventTarget.FALL:
                        result.append ("      return before != 0  &&  after == 0;\n");
                        break;
                    case EventTarget.RISE:
                    default:
                        result.append ("      return before == 0  &&  after != 0;\n");
                }
                result.append ("    }\n");
            }
            result.append ("  }\n");
            result.append ("  return false;\n");
            result.append ("}\n");
            result.append ("\n");

            if (bed.needLocalEventDelay)
            {
                result.append (T + " " + ns + "eventDelay (int i)\n");
                result.append ("{\n");
                context.defined.clear ();

                result.append ("  switch (i)\n");
                result.append ("  {\n");
                for (EventTarget et : bed.eventTargets)
                {
                    if (et.delay >= -1) continue;

                    // Need to evaluate expression
                    result.append ("    case " + et.valueIndex + ":\n");
                    result.append ("    {\n");
                    for (Variable v : et.dependencies)
                    {
                        multiconditional (v, context, "      ");
                    }
                    result.append ("      " + T + " result = ");
                    et.event.operands[1].render (context);
                    result.append (";\n");
                    result.append ("      if (result < 0) return -1;\n");
                    result.append ("      return result;\n");
                    result.append ("    }\n");
                }
                result.append ("  }\n");
                result.append ("  return -1;\n");
                result.append ("}\n");
                result.append ("\n");
            }

            result.append ("void " + ns + "setLatch (int i)\n");
            result.append ("{\n");
            result.append ("  flags |= (" + bed.localFlagType + ") 0x1 << i;\n");
            result.append ("}\n");
            result.append ("\n");

            if (bed.eventReferences.size () > 0)
            {
                result.append ("void " + ns + "finalizeEvent ()\n");
                result.append ("{\n");
                for (Variable v : bed.eventReferences)
                {
                    String current  = resolve (v.reference, context, false);
                    String buffered = resolve (v.reference, context, true);
                    result.append ("  " + current);
                    switch (v.assignment)
                    {
                        case Variable.ADD:
                            result.append (" += " + buffered + ";\n");
                            result.append ("  " + zero (buffered, v) + ";\n");
                            break;
                        case Variable.MULTIPLY:
                        case Variable.DIVIDE:
                        {
                            // The current and buffered values of the variable have the same exponent.
                            // raw = exponentV + exponentV
                            // shift = raw - exponentV = exponentV
                            int shift = v.exponent;
                            if (shift != 0  &&  fixedPoint)
                            {
                                result.append (" = (int64_t) " + current + " * " + buffered + RendererC.printShift (shift) + ";\n");
                            }
                            else
                            {
                                result.append (" *= " + buffered + ";\n");
                            }
                            result.append ("  " + clear (buffered, v, 1, context) + ";\n");
                            break;
                        }
                        case Variable.MIN:
                            result.append (" = min (" + current + ", " + buffered + ");\n");  // TODO: Write elementwise min() and max() for matrices.
                            result.append ("  " + clear (buffered, v, Double.POSITIVE_INFINITY, context) + ";\n");
                            break;
                        case Variable.MAX:
                            result.append (" = max (" + current + ", " + buffered + ");\n");
                            result.append ("  " + clear (buffered, v, Double.NEGATIVE_INFINITY, context) + ";\n");
                            break;
                        default:  // REPLACE
                            result.append (" = " + buffered + ";\n");
                            break;
                    }
                }
                result.append ("}\n");
                result.append ("\n");
            }
        }

        // Unit path
        if (bed.needLocalPath)
        {
            result.append ("void " + ns + "path (String & result)\n");
            result.append ("{\n");
            if (s.connectionBindings == null  ||  s.connectionBindings.size () == 1)  // Not a connection, or a unary connection
            {
                // We assume that result is passed in as the empty string.
                if (s.container != null)
                {
                    if (((BackendDataC) s.container.backendData).needLocalPath)  // Will our container provide a non-empty path?
                    {
                        result.append ("  container->path (result);\n");
                        result.append ("  result += \"." + s.name + "\";\n");
                    }
                    else
                    {
                        result.append ("  result = \"" + s.name + "\";\n");
                    }
                }
                if (bed.index != null)
                {
                    result.append ("  result += " + mangle ("$index") + ";\n");
                }
                else if (s.connectionBindings != null)
                {
                    ConnectionBinding c = s.connectionBindings.get (0);
                    BackendDataC cbed = (BackendDataC) c.endpoint.backendData;
                    if (cbed.index != null) result.append ("  result += " + mangle (c.alias) + "->" + mangle ("$index") + ";\n");
                }
            }
            else  // binary or higher connection
            {
                boolean first = true;
                boolean temp  = false;
                for (ConnectionBinding c : s.connectionBindings)
                {
                    if (first)
                    {
                        result.append ("  " + mangle (c.alias) + "->path (result);\n");
                        first = false;
                    }
                    else
                    {
                        if (! temp)
                        {
                            result.append ("  String temp;\n");
                            temp = true;
                        }
                        result.append ("  " + mangle (c.alias) + "->path (temp);\n");
                        result.append ("  result += \"-\";\n");
                        result.append ("  result += temp;\n");
                    }
                }
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit conversions
        Set<Conversion> conversions = s.getConversions ();
        for (Conversion pair : conversions)
        {
            EquationSet source = pair.from;
            EquationSet target = pair.to;
            boolean connectionSource = source.connectionBindings != null;
            boolean connectionTarget = target.connectionBindings != null;
            if (connectionSource != connectionTarget)
            {
                // A connection *must* know the instances it connects, while a compartment
                // cannot know those instances. Thus, one can never be converted to the other.
                PrintStream err = Backend.err.get ();
                if (connectionTarget) err.println ("Can't change $type from compartment to connection.");
                else                  err.println ("Can't change $type from connection to compartment.");
                err.println ("\tsource part:\t"      + (source.container == null ? source.name : source.prefix ()));
                err.println ("\tdestination part:\t" + (target.container == null ? target.name : target.prefix ()));
                throw new Backend.AbortRun ();
            }

            // The "Convert" functions only have local meaning, so they are never virtual.
            // Must do everything init() normally does, including increment $n.
            // Parameters:
            //   from -- the source part
            //   $type -- The integer index, in the $type expression, of the current target part. The target part's $type field will be initialized with this number (and zeroed after one cycle).
            result.append ("void " + ns + "Convert" + mangle (source.name) + mangle (target.name) + " (" + prefix (source) + " * from, int " + mangle ("$type") + ")\n");
            result.append ("{\n");
            String pt = prefix (target);
            result.append ("  " + pt + " * to = (" + pt + " *) " + mangle (target.name) + ".allocate ();\n");  // if this is a recycled part, then clear() is called
            result.append ("  " + mangle (target.name) + ".add (to);\n");
            BackendDataC tbed = (BackendDataC) target.backendData;
            if (tbed.pathToContainer == null)
            {
                result.append ("  to->container = this;\n");
            }
            if (connectionTarget)
            {
                // Match connection bindings
                for (ConnectionBinding c : target.connectionBindings)
                {
                    ConnectionBinding d = source.findConnection (c.alias);
                    if (d == null)
                    {
                        PrintStream err = Backend.err.get ();
                        err.println ("Unfulfilled connection binding during $type change: " + c.alias);
                        err.println ("\tsource part:\t"      + (source.container == null ? source.name : source.prefix ()));
                        err.println ("\tdestination part:\t" + (target.container == null ? target.name : target.prefix ()));
                        throw new Backend.AbortRun ();
                    }
                    result.append ("  to->" + mangle (c.alias) + " = from->" + mangle (c.alias) + ";\n");
                }
            }
            // TODO: Convert contained populations from matching populations in the source part?

            // Match variables between the two sets.
            // TODO: a match between variables should be marked as a dependency. That should be done much earlier by the middle end.
            for (Variable v : tbed.localMembers)
            {
                if (v.name.equals ("$type"))
                {
                    result.append ("  to->" + mangle (v) + " = " + mangle ("$type") + ";\n");  // initialize new part with its position in the $type split
                    continue;
                }
                if (v.name.equals ("$index")) continue;

                Variable v2 = source.find (v);
                if (v2 == null) continue;
                if (v2.assignment != Variable.REPLACE) continue;
                result.append ("  to->" + mangle (v) + " = " + resolve (v2.reference, context, false, "from->", false) + ";\n");
            }
            result.append ("  to->init ();\n");  // Unless the user qualifies code with $type, the values just copied above will simply be overwritten.

            result.append ("}\n");
            result.append ("\n");
        }
    }

    public void eventGenerate (String pad, EventTarget et, RendererC context, boolean multi)
    {
        String eventSpike = "EventSpike";
        if (multi) eventSpike += "Multi";
        else       eventSpike += "Single";
        String eventSpikeLatch = eventSpike + "Latch<" + T + ">";
        eventSpike += "<" + T + ">";

        StringBuilder result = context.result;
        if (et.delay >= -1)  // delay is a constant, so do all tests at the Java level
        {
            if (et.delay < 0)  // timing is no-care
            {
                result.append (pad + eventSpike + " * spike = new " + eventSpikeLatch + ";\n");
                result.append (pad + "spike->t = " + SIMULATOR + "currentEvent->t;\n");  // queue immediately after current cycle, so latches get set for next full cycle
            }
            else if (et.delay == 0)  // process as close to current cycle as possible
            {
                result.append (pad + eventSpike + " * spike = new " + eventSpike + ";\n");  // fully execute the event (not latch it)
                result.append (pad + "spike->t = " + SIMULATOR + "currentEvent->t;\n");  // queue immediately
            }
            else
            {
                boolean quantizedConstant = false;  // Indicates the delay is known constant and falls on a regular time-step.
                Variable dt = context.part.findDt ();
                if (dt != null  &&  dt.hasAttribute ("constant"))  // constant $t'
                {
                    double quantum = ((Scalar) dt.type).value;
                    double ratio   = et.delay / quantum;
                    int    step    = (int) Math.round (ratio);
                    quantizedConstant = Math.abs (ratio - step) < 1e-3;
                    if (quantizedConstant)
                    {
                        double delay = step * quantum;
                        result.append (pad + eventSpike + " * spike = new " + (during ? eventSpikeLatch : eventSpike) + ";\n");
                        result.append (pad + "spike->t = " + SIMULATOR + "currentEvent->t + " + context.print (delay, dt.exponent) + ";\n");
                    }
                }

                if (! quantizedConstant)
                {
                    int exponent = context.part.getRoot ().find (new Variable ("$t", 1)).exponent;
                    result.append (pad + T + " delay = " + context.print (et.delay, exponent) + ";\n");
                    result.append (pad + eventSpike + " * spike;\n");
                    eventGenerate (pad, et, context, eventSpike, eventSpikeLatch);
                }
            }
        }
        else  // delay must be evaluated, so emit tests at C level
        {
            result.append (pad + T + " delay = p->eventDelay (" + et.valueIndex + ");\n");
            result.append (pad + eventSpike + " * spike;\n");
            result.append (pad + "if (delay < 0)\n");
            result.append (pad + "{\n");
            result.append (pad + "  " + eventSpike + " * spike = new " + eventSpikeLatch + ";\n");
            result.append (pad + "  spike->t = " + SIMULATOR + "currentEvent->t;\n");
            result.append (pad + "}\n");
            result.append (pad + "else if (delay == 0)\n");
            result.append (pad + "{\n");
            result.append (pad + "  " + eventSpike + " * spike = new " + eventSpike + ";\n");
            result.append (pad + "  spike->t = " + SIMULATOR + "currentEvent->t;\n");
            result.append (pad + "}\n");
            result.append (pad + "else\n");
            result.append (pad + "{\n");
            eventGenerate (pad + "  ", et, context, eventSpike, eventSpikeLatch);
            result.append (pad + "}\n");
        }

        result.append (pad + "spike->latch = " + et.valueIndex + ";\n");
        if (multi) result.append (pad + "spike->targets = &eventMonitor_" + prefix (et.container) + ";\n");
        else       result.append (pad + "spike->target = p;\n");
        result.append (pad + "" + SIMULATOR + "queueEvent.push (spike);\n");
    }

    public void eventGenerate (String pad, EventTarget et, RendererC context, String eventSpike, String eventSpikeLatch)
    {
        StringBuilder result = context.result;

        // Is delay close enough to a time-quantum?
        if (fixedPoint)
        {
            result.append (pad + "int step = (delay + event->dt / 2) / event->dt;\n");
            result.append (pad + "int quantizedTime = step * event->dt;\n");
            result.append (pad + "if (quantizedTime == delay)\n");  // All fractional bits are zero. Usually there are no more than 10 fractional bits (~1/1000 of a time step).
        }
        else
        {
            result.append (pad + T + " ratio = delay / event->dt;\n");
            result.append (pad + "int step = (int) round (ratio);\n");
            result.append (pad + "if (abs (ratio - step) < 1e-3)\n");
        }
        result.append (pad + "{\n");
        if (during)
        {
            result.append (pad + "  spike = new " + eventSpikeLatch + ";\n");
        }
        else
        {
            result.append (pad + "  spike = new " + eventSpike + ";\n");
        }
        if (fixedPoint)
        {
            result.append (pad + "  delay = quantizedTime;\n");
        }
        else
        {
            result.append (pad + "  delay = step * event->dt;\n");
        }
        result.append (pad + "}\n");
        result.append (pad + "else\n");
        result.append (pad + "{\n");
        result.append (pad + "  spike = new " + eventSpike + ";\n");
        result.append (pad + "}\n");
        result.append (pad + "spike->t = " + SIMULATOR + "currentEvent->t + delay;\n");
    }

    /**
        Emit the equations associated with a variable.
        Assumes that phase indicators have already been factored out by simplify().
    **/
    public void multiconditional (Variable v, RendererC context, String pad) throws Exception
    {
        boolean connect = context.part.getConnect ();
        boolean init    = context.part.getInit ();
        boolean isType  = v.name.equals ("$type");

        if (v.hasAttribute ("temporary")) context.result.append (pad + type (v) + " " + mangle (v) + ";\n");

        // Select the default equation
        EquationEntry defaultEquation = null;
        for (EquationEntry e : v.equations) if (e.ifString.isEmpty ()) defaultEquation = e;

        // Initialize static objects, and dump dynamic objects needed by conditions
        for (EquationEntry e : v.equations)
        {
            if (e.condition != null)
            {
                prepareDynamicObjects1 (e.condition, context, init, pad);
                prepareDynamicObjects2 (e.condition, context, init, pad);
            }
        }

        // Write the conditional equations
        boolean haveIf = false;
        String padIf = pad;
        for (EquationEntry e : v.equations)
        {
            if (e == defaultEquation) continue;  // Must skip the default equation, as it will be emitted last.

            if (e.condition != null)
            {
                String ifString;
                if (haveIf)
                {
                    ifString = "else if (";
                }
                else
                {
                    ifString = "if (";
                    haveIf = true;
                    padIf = pad + "  ";
                }
                context.result.append (pad + ifString);
                e.condition.render (context);
                context.result.append (")\n");
                context.result.append (pad + "{\n");
            }
            if (isType)
            {
                // Set $type to an integer index indicating which of the splits statements in this equation set
                // was actually triggered. During finalize(), this will select a piece of code that implements
                // this particular split. Afterward, $type will be set to an appropriate index within the split,
                // per the N2A language document.
                if (! (e.expression instanceof Split))
                {
                    Backend.err.get ().println ("Unexpected expression for $type");
                    throw new Backend.AbortRun ();
                }
                int index = context.part.splits.indexOf (((Split) e.expression).parts);
                context.result.append (padIf + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                prepareDynamicObjects1 (e.expression, context, init, padIf);
                prepareDynamicObjects2 (e.expression, context, init, padIf);
                context.result.append (padIf);
                renderEquation (context, e);
            }
            if (haveIf) context.result.append (pad + "}\n");
        }

        // Write the default equation
        if (defaultEquation == null)
        {
            if (v.hasAttribute ("temporary"))
            {
                if (haveIf)
                {
                    context.result.append (pad + "else\n");
                    context.result.append (pad + "{\n");
                }
                context.result.append (padIf + zero (resolve (v.reference, context, true), v) + ";\n");
                if (haveIf) context.result.append (pad + "}\n");
            }
            else
            {
                String defaultValue = null;
                if (isType)
                {
                    defaultValue = "0";  // always reset $type to 0
                }
                else if (connect  &&  v.name.equals ("$p"))
                {
                    defaultValue = "1";
                }
                else
                {
                    // External-write variables with a combiner get reset during finalize.
                    // However, buffered variables with simple assignment (REPLACE) need
                    // to copy forward the current buffered value.
                    if (   v.assignment == Variable.REPLACE
                        && v.reference.variable.container == v.container   // local to the equation set, not a reference to an outside variable
                        && v.equations.size () > 0
                        && v.hasAny ("cycle", "externalRead")  // buffered
                        && ! v.hasAttribute ("initOnly")
                        && ! init  &&  ! connect)  // not in a phase that skips buffering
                    {
                        defaultValue = resolve (v.reference, context, false);  // copy previous value
                    }
                }

                if (defaultValue != null)
                {
                    if (haveIf)
                    {
                        context.result.append (pad + "else\n");
                        context.result.append (pad + "{\n");
                    }
                    context.result.append (padIf + resolve (v.reference, context, true) + " = " + defaultValue + ";\n");
                    if (haveIf) context.result.append (pad + "}\n");
                }
            }
        }
        else
        {
            if (haveIf)
            {
                context.result.append (pad + "else\n");
                context.result.append (pad + "{\n");
            }
            if (isType)
            {
                ArrayList<EquationSet> split = ((Split) defaultEquation.expression).parts;
                int index = context.part.splits.indexOf (split);
                context.result.append (padIf + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                prepareDynamicObjects1 (defaultEquation.expression, context, init, pad);
                prepareDynamicObjects2 (defaultEquation.expression, context, init, pad);

                // Special case -- Don't generate equation if this is a constructed string going straight to the variable.
                // The following is only satisfied when this is a single unconditional equation.
                if (defaultEquation.expression instanceof Add)
                {
                    Add a = (Add) defaultEquation.expression;
                    if (a.name != null  &&  a.name.equals (mangle (v))) return;
                }

                context.result.append (padIf);
                renderEquation (context, defaultEquation);
            }
            if (haveIf) context.result.append (pad + "}\n");
        }
    }

    public void renderEquation (RendererC context, EquationEntry e)
    {
        StringBuilder result = context.result;
        if (e.variable.hasAttribute ("dummy"))
        {
            e.expression.render (context);
        }
        else
        {
            String LHS = resolve (e.variable.reference, context, true);
            result.append (LHS);
            int shift = 0;
            switch (e.variable.assignment)
            {
                case Variable.REPLACE:
                    result.append (" = ");
                    break;
                case Variable.ADD:
                    result.append (" += ");
                    break;
                case Variable.MULTIPLY:
                    // raw exponent = exponentV + exponentExpression
                    // shift = raw - exponentV = exponentExpression
                    shift = e.expression.exponentNext;
                    if (shift != 0  &&  fixedPoint)
                    {
                        if (shift < 0) result.append (" = (int64_t) " + LHS + " * ");
                        else           result.append (" = "           + LHS + " * ");
                    }
                    else
                    {
                        result.append (" *= ");
                    }
                    break;
                case Variable.DIVIDE:
                    // raw = exponentV - exponentExpression
                    // shift = raw - exponentV = -exponentExpression
                    shift = -e.expression.exponentNext;
                    if (shift != 0  &&  fixedPoint)
                    {
                        if (shift > 0) result.append (" = ((int64_t) " + LHS + RendererC.printShift (shift) + ") / ");
                        else           result.append (" = "            + LHS                                +  " / ");
                    }
                    else
                    {
                        result.append (" /= ");
                    }
                    break;
                case Variable.MIN:
                    result.append (" = min (" + LHS + ", ");
                    break;
                case Variable.MAX:
                    result.append (" = max (" + LHS + ", ");
            }

            boolean cli = e.variable.hasAttribute ("cli");
            boolean minmax = e.variable.assignment == Variable.MAX  ||  e.variable.assignment == Variable.MIN;
            if (cli) result.append ("params->get (\"" + e.variable.fullName () + "\", ");

            if (minmax) context.renderType (e.expression);
            else        e.expression.render (context);

            if (cli) result.append (")");
            if (minmax) result.append (")");
            if (shift != 0  &&  fixedPoint)
            {
                result.append (RendererC.printShift (shift));
            }
        }
        result.append (";\n");
    }

    public void prepareStaticObjects (Operator op, RendererC context, String pad)
    {
        BackendDataC bed = context.bed;

        Visitor visitor = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                for (ProvideOperator po : extensions)
                {
                    Boolean result = po.prepareStaticObjects (op, context, pad);
                    if (result != null) return result;
                }
                if (op instanceof Output)
                {
                    Output o = (Output) op;
                    if (! o.hasColumnName)  // column name is generated
                    {
                        if (context.global ? bed.needGlobalPath : bed.needLocalPath)
                        {
                            context.result.append (pad + "path (" + o.columnName + ");\n");
                            context.result.append (pad + o.columnName + " += \"." + o.variableName + "\";\n");
                        }
                        else
                        {
                            context.result.append (pad + o.columnName + " = \"" + o.variableName + "\";\n");
                        }
                    }
                    return true;  // Continue to drill down, because I/O functions can be nested.
                }
                if (op instanceof Input)
                {
                    Input i = (Input) op;
                    if (   i.operands[0] instanceof Constant
                        && i.usesTime ()  &&  ! context.global  &&  ! fixedPoint  // In the case of T==int, we don't need to set epsilon because it is already set to 1 by the constructor.
                        && ! context.defined.contains (i.name))
                    {
                        // See comments in generateMainInitializers()
                        context.defined.add (i.name);

                        // Read $t' as an lvalue, to ensure we get any newly-set frequency.
                        // However, can't do this if $t' is a constant. In that case, no variable exists.
                        boolean lvalue = ! bed.dt.hasAttribute ("constant");
                        context.result.append (pad + i.name + "->epsilon = " + resolve (bed.dt.reference, context, lvalue) + " / 1000.0");
                        if (T.equals ("float")) context.result.append ("f");
                        context.result.append (";\n");
                    }
                    return true;
                }
                return true;
            }
        };
        op.visit (visitor);
    }

    /**
        Build complex sub-expressions into a single local variable that can be referenced by the equation.
        Pass 1 -- Strings and matrix expressions.
    **/
    public void prepareDynamicObjects1 (Operator op, RendererC context, boolean init, String pad)
    {
        Visitor visitor1 = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof BuildMatrix)
                {
                    BuildMatrix m = (BuildMatrix) op;
                    int rows = m.getRows ();
                    int cols = m.getColumns ();
                    context.result.append (pad + "MatrixFixed<" + T + "," + rows + "," + cols + "> " + m.name + ";\n");
                    for (int r = 0; r < rows; r++)
                    {
                        if (cols == 1)
                        {
                            context.result.append (pad + m.name + "[" + r + "] = ");
                            m.operands[0][r].render (context);
                            context.result.append (";\n");
                        }
                        else
                        {
                            for (int c = 0; c < cols; c++)
                            {
                                context.result.append (pad + m.name + "(" + r + "," + c + ") = ");
                                m.operands[c][r].render (context);
                                context.result.append (";\n");
                            }
                        }
                    }
                    return false;
                }
                if (op instanceof Add)
                {
                    Add a = (Add) op;
                    if (a.name != null)
                    {
                        if (! (a.parent instanceof Variable)  ||  ! mangle ((Variable) a.parent).equals (a.name))
                        {
                            context.result.append (pad + "String " + a.name + ";\n");
                        }
                        for (Operator o : flattenAdd (a))
                        {
                            context.result.append (pad + a.name + " += ");
                            o.render (context);
                            context.result.append (";\n");
                        }
                        return false;
                    }
                }
                return true;
            }
        };
        op.visit (visitor1);
    }

    /**
        Build complex sub-expressions into a single local variable that can be referenced by the equation.
        Pass 2 -- I/O functions.
    **/
    public void prepareDynamicObjects2 (Operator op, RendererC context, boolean init, String pad)
    {
        BackendDataC bed = context.bed;

        Visitor visitor2 = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (! (op instanceof Function)) return true;  // Everything we care about below is an IO function.

                // If filename is given by a variable, then it could be used more than once,
                // so we need to guard against repeats.
                // If filename is an expression, each occurrence is unique.
                // If filename is constant, we don't deal with it here.
                Variable v = null;
                Function f = (Function) op;
                if (f.operands.length > 0)
                {
                    Operator operand0 = ((Function) op).operands[0];
                    if (operand0 instanceof AccessVariable) v = ((AccessVariable) operand0).reference.variable;
                }

                if (op instanceof ReadMatrix)
                {
                    ReadMatrix r = (ReadMatrix) op;
                    if (! (r.operands[0] instanceof Constant))
                    {
                        if (v != null)
                        {
                            if (context.defined.contains (v)) return true;
                            context.defined.add (v);
                        }
                        context.result.append (pad + "MatrixInput<" + T + "> * " + r.name + " = matrixHelper<" + T + "> (" + r.fileName);
                        if (fixedPoint) context.result.append (", " + r.exponent);
                        context.result.append (");\n");
                    }
                    return true;
                }
                if (op instanceof Mfile)
                {
                    Mfile m = (Mfile) op;
                    if (! (m.operands[0] instanceof Constant))
                    {
                        if (v != null)
                        {
                            if (context.defined.contains (v)) return true;
                            context.defined.add (v);
                        }
                        context.result.append (pad + "Mfile<" + T + "> * " + m.name + " = MfileHelper<" + T + "> (" + m.fileName + ");\n");
                    }
                    return true;
                }
                if (op instanceof Input)
                {
                    Input i = (Input) op;
                    if (! (i.operands[0] instanceof Constant))
                    {
                        if (v != null)
                        {
                            if (context.defined.contains (v)) return true;
                            context.defined.add (v);
                        }
                        context.result.append (pad + "InputHolder<" + T + "> * " + i.name + " = inputHelper<" + T + "> (" + i.fileName);
                        if (fixedPoint) context.result.append (", " + i.exponent + ", " + i.exponentRow);
                        context.result.append (");\n");

                        boolean smooth =             i.getKeywordFlag ("smooth");
                        boolean time   = smooth  ||  i.getKeywordFlag ("time");
                        if (time)
                        {
                            if (time)   context.result.append (pad + i.name + "->time = true;\n");
                            if (smooth) context.result.append (pad + i.name + "->smooth = true;\n");
                            if (! context.global  &&  ! fixedPoint)
                            {
                                boolean lvalue = ! bed.dt.hasAttribute ("constant");
                                context.result.append (pad + i.name + "->epsilon = " + resolve (bed.dt.reference, context, lvalue) + " / 1000.0");
                                if (T.equals ("float")) context.result.append ("f");
                                context.result.append (";\n");
                            }
                        }
                    }
                    return true;
                }
                if (op instanceof Output)
                {
                    Output o = (Output) op;
                    if (! (o.operands[0] instanceof Constant))
                    {
                        if (v != null)
                        {
                            if (context.defined.contains (v)) return true;
                            context.defined.add (v);
                        }
                        context.result.append (pad + "OutputHolder<" + T + "> * " + o.name + " = outputHelper<" + T + "> (" + o.fileName + ");\n");
                        if (o.getKeywordFlag ("raw"))
                        {
                            context.result.append (pad + o.name + "->raw = true;\n");
                        }
                    }
                    return true;
                }
                if (op instanceof ReadImage)
                {
                    ReadImage r = (ReadImage) op;
                    if (! (r.operands[0] instanceof Constant))
                    {
                        if (v != null)
                        {
                            if (context.defined.contains (v)) return true;
                            context.defined.add (v);
                        }
                        context.result.append (pad + "ImageInput<" + T + "> * " + r.name + " = imageInputHelper<" + T + "> (" + r.fileName + ");\n");
                    }
                    return true;
                }
                if (op instanceof Draw)
                {
                    Draw d = (Draw) op;

                    // Use a slightly different logical structure here, because every draw() call
                    // should set its keyword parameters, even if the target has already been created.
                    if (! (d.operands[0] instanceof Constant)  &&  (v == null  ||  ! context.defined.contains (v)))
                    {
                        context.result.append (pad + "ImageOutput<" + T + "> * " + d.name + " = imageOutputHelper<" + T + "> (" + d.fileName + ");\n");
                        if (v != null) context.defined.add (v);
                    }

                    boolean light     = d instanceof DrawLight;
                    boolean material  = d instanceof Draw3D;
                    boolean gl        = glLibs != null;
                    boolean ffmpeg    = ffmpegLibDir != null;
                    boolean do3D      = gl  &&  (material  ||  light);
                    boolean needModel = material  &&  ((Draw3D) d).needModelMatrix ();
                    boolean modelSet  = false;
                    int     index     = 0;

                    if (do3D)
                    {
                        // "light", "material" and "model" are reserved local variables
                        // The keyword section below will assign specific values.
                        if (light)
                        {
                            if (! context.defined.contains ("light"))
                            {
                                context.defined.add ("light");
                                context.result.append (pad + "Light * light;\n");
                            }

                            if (d.operands.length > 1) index = (int) d.operands[1].getDouble ();

                            boolean on = true;
                            Operator kon = d.getKeyword ("on");
                            if (kon != null) on = kon.getDouble () != 0;
                            if (on)
                            {
                                context.result.append (pad + "light = " + d.name + "->addLight (" + index + ");\n");
                            }
                            else
                            {
                                context.result.append (pad + d.name + "->removeLight (" + index + ");\n");
                            }
                        }
                        else  // material must be true
                        {
                            if (! context.defined.contains ("material"))
                            {
                                context.defined.add ("material");
                                context.result.append (pad + "Material material;\n");
                            }
                        }
                        if (needModel)
                        {
                            if (! context.defined.contains ("model"))
                            {
                                context.defined.add ("model");
                                context.result.append (pad + "Matrix<" + T + ",4,4> model;\n");  // model is built in type T, then converted to float on call to draw routine
                            }
                        }
                    }
                    else
                    {
                        // For simplicity of testing later.
                        light = false;
                        material = false;
                    }

                    if (d.keywords != null)
                    {
                        for (Entry<String,Operator> k : d.keywords.entrySet ())
                        {
                            String   key   = k.getKey ();
                            Operator value = k.getValue ();
                            boolean  convertToFloat = false;
                            switch (key)
                            {
                                // A standard assignment comes after this switch statement.
                                // If a case ends with "break", it falls through to the standard assignment.
                                // If a case ends with "continue", it skips the standard assignment.

                                // Scalars that are explicitly floating-point, regardless of T.
                                case "spotExponent":
                                case "spotCutoff":
                                case "attenuation0":
                                case "attenuation1":
                                case "attenuation2":
                                    if (! light) continue;
                                    context.result.append (pad + "light->" + key);
                                    convertToFloat = true;
                                    break;
                                case "shininess":
                                    if (! material) continue;
                                    context.result.append (pad + "material.shininess");
                                    convertToFloat = true;
                                    break;
                                case "timeScale":
                                    if (! ffmpeg) continue;  // "timeScale" only applies to FFmpeg.
                                    context.result.append (pad + d.name + "->timeScale");
                                    convertToFloat = true;
                                    break;

                                case "codec":  // string
                                    if (! ffmpeg) continue;  // "codec" only applies to FFmpeg.
                                case "hold":  // boolean
                                case "format":  // string
                                    context.result.append (pad + d.name + "->" + key);
                                    break;

                                case "width":  // int (pixels)
                                    context.result.append (pad + d.name + "->width");
                                    if (! d.keywords.containsKey ("height")) context.result.append (" = " + d.name + "->height");
                                    break;
                                case "height":  // int (pixels)
                                    context.result.append (pad + d.name + "->height");
                                    if (! d.keywords.containsKey ("width")) context.result.append (" = " + d.name + "->width");
                                    break;

                                // Vectors that require conversion to float
                                case "position":
                                case "direction":
                                    if (! light) continue;
                                    context.result.append (pad + "setVector (light->" + key + ", ");
                                    value.render (context);
                                    if (fixedPoint) context.result.append (", " + value.exponent);
                                    context.result.append (");\n");
                                    continue;

                                // Matrices that require conversion to float
                                case "view":
                                case "projection":
                                    if (! gl) continue;
                                    String name = d.name + "->next" + key.substring (0, 1).toUpperCase () + key.substring (1);
                                    context.result.append (pad + name + " = ");
                                    value.render (context);
                                    context.result.append (";\n");
                                    if (fixedPoint) context.result.append (pad + name + " /= powf (2, - " + value.exponent + ");\n");
                                    continue;

                                // The model matrix will get coerced to float in the call to draw.
                                // If this is fixed point, we will also pass the exponent.
                                // All that happens later. For now, just assign the matrix.
                                case "model":
                                    if (! needModel) continue;
                                    modelSet = true;
                                    context.result.append (pad + "model");
                                    break;

                                case "clear":
                                    context.result.append (pad + d.name + "->setClearColor (");
                                    value.render (context);
                                    context.result.append (");\n");
                                    continue;

                                case "specular":
                                    if (light) continue;
                                case "diffuse":
                                case "ambient":
                                case "emission":
                                    if (light)
                                    {
                                        context.result.append (pad + "setVector (light->" + key + ", ");
                                        value.render (context);
                                        context.result.append (");\n");
                                    }
                                    else if (material)
                                    {
                                        context.result.append (pad + "setColor (material." + key + ", ");
                                        value.render (context);
                                        context.result.append (", " + key.equals ("diffuse") + ");\n");
                                    }
                                    continue;

                                default:  // "raw" and any invalid keywords
                                    continue;
                            }
                            context.result.append (" = ");
                            if (fixedPoint  &&  convertToFloat) context.result.append ("(");
                            value.render (context);
                            if (fixedPoint  &&  convertToFloat) context.result.append (") / powf (2, - " + value.exponent + ")");
                            context.result.append (";\n");
                        }
                    }

                    // Finish preparing model matrix.
                    if (needModel  &&  ! modelSet)
                    {
                        context.result.append (pad + "identity (model);\n");
                    }

                    return true;
                }
                return true;
            }
        };
        op.visit (visitor2);
    }

    public List<Operator> flattenAdd (Add add)
    {
        ArrayList<Operator> result = new ArrayList<Operator> ();
        if (add.operand0 instanceof Add) result.addAll (flattenAdd ((Add) add.operand0));
        else                             result.add (add.operand0);
        if (add.operand1 instanceof Add) result.addAll (flattenAdd ((Add) add.operand1));
        else                             result.add (add.operand1);
        return result;
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

    /**
        Converts identifiers into a form that can be compiled.
        Legitimate identifiers in our language follow essentially the same
        rules as Java or C++, with the addition of three characters:
        space, period and single-quote. Here we assume that Java identifier
        functions also satisfy C++ rules, and simply replace our additional
        characters with underscores. There are some degenerate cases where
        this won't produce a unique identifier. Examples:
            A.B and A_B are both in the model
            A' and A_ are both in the model
        These are unlikely in practice, and ignoring them will produce more readable code.
    **/
    public String mangle (String prefix, String input)
    {
        // Compare with NodePart.validIdentifierFrom()
        // Assuming a non-empty prefix, we don't worry about valid start character.
        // We also change spaces to underscores.
        if (supportsUnicodeIdentifiers)
        {
            input = input.trim ();

            StringBuilder result = new StringBuilder ();
            for (char c : input.toCharArray ())
            {
                if (Character.isJavaIdentifierPart (c)) result.append (c);
                else                                    result.append ('_');
            }

            return prefix + result;
        }

        // Old-school mangling
        // Just like the above method, this is not guaranteed to create unique names,
        // but the failure case are rather pathological.
        StringBuilder result = new StringBuilder (prefix);
        for (char c : input.toCharArray ())
        {
            // Even though underscore (_) is a legitimate character,
            // we don't use it.  Instead it is used as an escape for unicode.
            // We use variable length unicode values because there is no need to parse
            // the identifiers back into wide characters.
            if (   ('a' <= c && c <= 'z')
                || ('A' <= c && c <= 'Z')
                || ('0' <= c && c <= '9'))
            {
                result.append (c);
            }
            else
            {
                result.append ("_" + Integer.toHexString (c));
            }
        }
        return result.toString ();
    }

    public String type (Variable v)
    {
        if (v.type instanceof Matrix)
        {
            if (v.hasAttribute ("MatrixPointer")) return "MatrixAbstract<" + T + "> *";
            Matrix m = (Matrix) v.type;
            int rows = m.rows ();
            int cols = m.columns ();
            if (rows > 0  &&  cols > 0) return "MatrixFixed<" + T + "," + rows + "," + cols + ">";  // Known dimension, so use more efficient storage.
            return "Matrix<" + T + ">";  // Arbitrary dimension
        }
        if (v.type instanceof Text) return "String";
        return T;
    }

    public String zero (String name, Variable v) throws Exception
    {
        if (v.type instanceof Scalar) return name + " = 0";
        if (v.type instanceof Matrix) return "::clear (" + name + ")";  // Don't check for matrix pointer, because zero() should never be called for such variables.
        if (v.type instanceof Text  ) return name + ".clear ()";
        throw new RuntimeException ("Variable was not assigned a type.");  // This indicates an error in the compiler or backend code.
    }

    public String clear (String name, Variable v, double value, RendererC context) throws Exception
    {
        String p = context.print (value, v.exponent);
        if (v.type instanceof Scalar) return name + " = " + p;
        if (v.type instanceof Matrix) return "::clear (" + name + ", " + p + ")";  // Don't check for matrix pointer, because clear() should never be called for such variables.
        if (v.type instanceof Text  ) return name + ".clear ()";
        throw new RuntimeException ("Variable was not assigned a type.");
    }

    public String clearAccumulator (String name, Variable v, RendererC context) throws Exception
    {
        switch (v.assignment)
        {
            case Variable.MULTIPLY:
            case Variable.DIVIDE:   return clear (name, v, 1,                        context);
            case Variable.MIN:      return clear (name, v, Double.POSITIVE_INFINITY, context);
            case Variable.MAX:      return clear (name, v, Double.NEGATIVE_INFINITY, context);
            case Variable.ADD:
            default:                return zero (name, v);
        }
    }

    public String prefix (EquationSet t)
    {
        if (t == null) return "Wrapper";
        String result = mangle (t.name);
        while (t != null)
        {
            t = t.container;
            if (t != null) result = mangle (t.name) + "_" + result;
        }
        return result;
    }

    public String resolve (VariableReference r, RendererC context, boolean lvalue)
    {
        return resolve (r, context, lvalue, "", false);
    }

    /**
        @param v A variable to convert into C++ code that can access it at runtime.
        @param context For the AST rendering system.
        @param lvalue Indicates that this will receive a value assignment. The other case is an rvalue, which will simply be read.
        @param base Injects a pointer at the beginning of the resolution path.
        @param logical The intended use is in a boolean expression, such as an if-test.
    **/
    public String resolve (VariableReference r, RendererC context, boolean lvalue, String base, boolean logical)
    {
        if (r == null  ||  r.variable == null) return "unresolved";
        Variable v = r.variable;

        // Because $live has some rather complex access rules, take special care to ensure
        // that it always returns false when either $connect or $init are true.
        if (v.name.equals ("$live")  &&  v.container == context.part)
        {
            if (context.part.getConnect ()  ||  context.part.getInit ()) return "0";
        }

        if (v.hasAttribute ("constant")  &&  ! lvalue)  // A constant will always be an rvalue, unless it is being loaded into a local variable (special case for $t').
        {
            EquationEntry e = v.equations.first ();
            StringBuilder temp = context.result;
            StringBuilder result = new StringBuilder ();
            context.result = result;
            e.expression.render (context);
            context.result = temp;
            return result.toString ();
        }

        String containers = resolveContainer (r, context, base);

        if (v.hasAttribute ("instance"))
        {
            return stripDereference (containers);
        }

        String name = "";
        BackendDataC vbed = (BackendDataC) v.container.backendData;  // NOT context.bed !
        if (v.hasAttribute ("preexistent"))
        {
            if (! v.name.startsWith ("$"))
            {
                return v.name;  // most likely a local variable, for example "rc" in mapIndex()
            }
            else
            {
                if (v.name.equals ("$t"))
                {
                    if (! lvalue  &&  v.order == 0)
                    {
                        name = SIMULATOR + "currentEvent->t";
                    }
                    // For lvalue, fall through to the main case below.
                    // Higher orders of $t should not be "preexistent". They are handled below.
                }
                else if (v.name.equals ("$n"))
                {
                    if (! lvalue  &&  v.order == 0)
                    {
                        name = "n";
                    }
                }
            }
        }
        if (v.name.equals ("$live"))
        {
            if (lvalue) return "unresolved";
            if (v.hasAttribute ("accessor")) return containers + "getLive ()";

            // Not "constant" or "accessor", so must be direct access.
            if (logical) return  "(" + vbed.getFlag (containers + "flags", false, vbed.liveFlag) + ")";
            else         return "((" + vbed.getFlag (containers + "flags", false, vbed.liveFlag) + ") ? 1 : 0)";
        }
        else if (v.hasAttribute ("accessor"))
        {
            if (v.name.equals ("$t")  &&  v.order == 1  &&  ! lvalue)  // $t'
            {
                return containers + "getDt ()";
            }
            return "unresolved";  // Only $live and $t' can have "accessor" attribute.
        }
        if (v.name.endsWith (".$count"))
        {
            if (lvalue) return "unresolved";
            String alias = v.name.substring (0, v.name.lastIndexOf ("."));
            name = mangle (alias) + "->" + prefix (v.container) + "_" + mangle (alias) + "_count";
        }
        if (name.length () == 0)
        {
            // Write to buffered value, except during init phase.
            if (lvalue  &&  ! context.part.getInit ()  &&  (vbed.globalBuffered.contains (v)  ||  vbed.localBuffered.contains (v)))
            {
                name = mangle ("next_", v);
            }
            else
            {
                name = mangle (v);
            }
        }
        if (v.hasAttribute ("MatrixPointer")  &&  ! lvalue)
        {
            return "(* " + containers + name + ")";  // Actually stored as a pointer
        }
        return containers + name;
    }

    /**
        Compute a series of pointers to get from current part to r.
        Result does not include the variable name itself.
    **/
    public String resolveContainer (VariableReference r, RendererC context, String base)
    {
        String containers = base;
        EquationSet current = context.part;
        boolean global = context.global;
        int last = r.resolution.size () - 1;
        for (int i = 0; i <= last; i++)
        {
            Object o = r.resolution.get (i);
            if (o instanceof EquationSet)  // We are following the containment hierarchy.
            {
                EquationSet s = (EquationSet) o;
                if (s.container == current)  // descend into one of our contained populations
                {
                    if (i == last  &&  r.variable.hasAttribute ("global"))  // descend to the population object
                    {
                        // No need to cast the population instance, because it is explicitly typed
                        containers += mangle (s.name) + ".";
                        global = true;
                    }
                    else  // descend to a singleton instance of the population.
                    {
                        BackendDataC bed = (BackendDataC) s.backendData;
                        if (! bed.singleton)
                        {
                            Backend.err.get ().println ("ERROR: Down-reference to population with more than one instance is ambiguous.");
                            throw new AbortRun ();
                        }
                        containers += mangle (s.name) + ".instance.";
                        global = false;
                    }
                }
                else  // ascend to our container
                {
                    containers = containerOf (current, global, containers);
                    global = false;
                }
                current = s;
            }
            else if (o instanceof ConnectionBinding)  // We are following a part reference, which implies we are a connection.
            {
                ConnectionBinding c = (ConnectionBinding) o;
                containers += mangle (c.alias) + "->";
                current = c.endpoint;
                global = false;
            }
        }

        if (r.variable.hasAttribute ("global")  &&  ! global)
        {
            // Must ascend to our container and then descend to our population object.
            containers = containerOf (current, global, containers);
            containers += mangle (current.name) + ".";
        }

        return containers;
    }

    /**
        We either have direct access to our container, or we are a connection using indirect access through an endpoint.
        @param global Indicates that the current context is a population class. Because Population::container
        is declared as a generic part in runtime.h, access requires a typecast.
    **/
    public String containerOf (EquationSet s, boolean global, String base)
    {
        BackendDataC bed = (BackendDataC) s.backendData;
        if (bed.pathToContainer != null  &&  ! global) base += mangle (bed.pathToContainer) + "->";
        base += "container";
        if (global) return "((" + prefix (s.container) + " *) " + base + ")->";  // Cast is needed for population member "container". s.container should always be non-null. IE: we don't reference up to Wrapper. If we do, it is directly coded rather than coming here.
        return base + "->";
    }

    public String stripDereference (String containers)
    {
        if (containers.endsWith ("->")) return containers.substring (0, containers.length () - 2);
        if (containers.endsWith ("." )) return containers.substring (0, containers.length () - 1);
        return containers;
    }

    /**
        Generate code to enumerate all instances of a connection endpoint. Handles deep hierarchical
        embedding.

        <p>A connection resolution can take 3 kinds of step:
        <ul>
        <li>Up to container
        <li>Down to a population
        <li>Through another connection
        </ul>

        @param current EquationSet associated with the context of the current step of resolution.
        @param pointer Name of a pointer to the context for the current step of resolution. Can
        be a chain of pointers. Can be empty if the code is to be emitted in the current context.
        @param depth Position in the resolution array of our next step.
        @param prefix Spaces to insert in front of each line to maintain nice indenting.
    **/
    public void assembleInstances (EquationSet current, boolean global, String pointer, List<Object> resolution, int depth, String prefix, StringBuilder result)
    {
        int last = resolution.size () - 1;
        for (int i = depth; i <= last; i++)
        {
            Object r = resolution.get (i);
            if (r instanceof EquationSet)
            {
                EquationSet s = (EquationSet) r;
                if (r == current.container)  // ascend to parent
                {
                    pointer = containerOf (current, global, pointer);
                    global = false;
                }
                else  // descend to child
                {
                    pointer += mangle (s.name) + ".";
                    global = true;
                    if (i < last)  // Enumerate the instances of child population.
                    {
                        if (depth == 0)
                        {
                            result.append (prefix + "result->instances = new vector<Part<" + T + "> *>;\n");
                            result.append (prefix + "result->deleteInstances = true;\n");
                        }
                        String it = "it" + i;
                        result.append (prefix + "for (auto " + it + " : " + pointer + "instances)\n");
                        result.append (prefix + "{\n");
                        assembleInstances (s, false, it + "->", resolution, i+1, prefix + "  ", result);
                        result.append (prefix + "}\n");
                        return;
                    }
                }
                current = s;
            }
            else if (r instanceof ConnectionBinding)
            {
                ConnectionBinding c = (ConnectionBinding) r;
                pointer += mangle (c.alias) + "->";
                global = false;
                current = c.endpoint;
            }
            // else something is broken. This case should never occur.
        }

        // "pointer" now references the target population or instance.
        BackendDataC bed = (BackendDataC) current.backendData;
        if (global)
        {
            // Collect its instances.
            if (bed.singleton)
            {
                if (depth == 0)
                {
                    result.append (prefix + "result->instances = new vector<Part<" + T + "> *>;\n");
                    result.append (prefix + "result->deleteInstances = true;\n");
                }
                result.append (prefix + "bool newborn = " + bed.getFlag (pointer + "instance.flags", false, bed.newborn) + ";\n");
                result.append (prefix + "if (result->firstborn == INT_MAX  &&  newborn) result->firstborn = result->instances->size ();\n");
                result.append (prefix + "result->instances->push_back (& " + pointer + "instance);\n");
            }
            else
            {
                if (depth == 0)  // No enumerations occurred during the resolution, so no list was created.
                {
                    // Simply reference the existing list of instances.
                    result.append (prefix + "result->firstborn = " + pointer + "firstborn;\n");
                    result.append (prefix + "result->instances = (vector<Part<" + T + "> *> *) & " + pointer + "instances;\n");
                }
                else  // Enumerations occurred, so we are already accumulating a list.
                {
                    // Append instances to accumulating list.
                    result.append (prefix + "if (result->firstborn == INT_MAX  &&  " + pointer + "firstborn < " + pointer + "instances.size ()) result->firstborn = result->instances->size () + " + pointer + "firstborn;\n");
                    result.append (prefix + "result->instances->insert (result->instances->end (), " + pointer + "instances.begin (), " + pointer + "instances.end ());\n");
                }
            }

            // Schedule the population to have its newborn flags cleared.
            // We assume that any newborn flags along the path to this population are either unimportant
            // or will get cleared elsewhere.
            result.append (prefix + "if (! (" + bed.getFlag (pointer + "flags", true, bed.clearNew) + "))\n");
            result.append (prefix + "{\n");
            result.append (prefix + "  " + bed.setFlag (pointer + "flags", true, bed.clearNew) + ";\n");
            pointer = stripDereference (pointer);
            if (pointer.isEmpty ()) pointer = "this";
            else                    pointer = "& " + pointer;
            result.append (prefix + "  " + SIMULATOR + "clearNew (" + pointer + ");\n");
            result.append (prefix + "}\n");
        }
        else  // Resolution led to an explicit instance. This is similar to a singleton.
        {
            if (depth == 0)
            {
                result.append (prefix + "result->instances = new vector<Part<" + T + "> *>;\n");
                result.append (prefix + "result->deleteInstances = true;\n");
            }
            result.append (prefix + "bool newborn = " + bed.getFlag (pointer + "flags", false, bed.newborn) + ";\n");
            result.append (prefix + "if (result->firstborn == INT_MAX  &&  newborn) result->firstborn = result->instances->size ();\n");
            result.append (prefix + "result->instances->push_back (" + stripDereference (pointer) + ");\n");
        }
    }

    /**
        Subroutine of generateDefinitionsGlobal() that determines if new instances have
        appeared in a target population. This is similar to assembleInstances(), except
        that different code is emitted. This code occurs within the population finalize()
        function, and it's main job is to set "shouldConnect".
    **/
    public void checkInstances (EquationSet current, boolean global, String pointer, List<Object> resolution, int depth, String prefix, StringBuilder result)
    {
        int last = resolution.size () - 1;
        for (int i = 0; i <= last; i++)
        {
            Object r = resolution.get (i);
            if (r instanceof EquationSet)
            {
                EquationSet s = (EquationSet) r;
                if (r == current.container)  // ascend to parent
                {
                    pointer = containerOf (current, global, pointer);
                    global = false;
                }
                else  // descend to child
                {
                    // TODO: Should we drill down to inner populations?
                    // The Internal backend stops at the first level of enumeration.
                    // The approach here is more work, but also more correct.
                    pointer += mangle (s.name) + ".";
                    global = true;
                    if (i < last)  // Enumerate the instances of child population.
                    {
                        String it = "it" + i;
                        result.append (prefix + "for (auto " + it + " : " + pointer + "instances)\n");
                        result.append (prefix + "{\n");
                        result.append (prefix + "  if (shouldConnect) break;\n");
                        checkInstances (s, false, it + "->", resolution, i+1, prefix + "  ", result);
                        result.append (prefix + "}\n");
                        return;
                    }
                }
                current = s;
            }
            else if (r instanceof ConnectionBinding)
            {
                ConnectionBinding c = (ConnectionBinding) r;
                pointer += mangle (c.alias) + "->";
                global = false;
                current = c.endpoint;
            }
        }

        // "pointer" now references the target population or instance.
        BackendDataC bed = (BackendDataC) current.backendData;
        if (global)
        {
            if (bed.singleton)
            {
                result.append (prefix + "if (" + bed.getFlag (pointer + "instance.flags", false, bed.newborn) + ") shouldConnect = true;\n");
            }
            else
            {
                result.append (prefix + "if (" + pointer + "firstborn < " + pointer + "instances.size ()) shouldConnect = true;\n");
            }
        }
        else
        {
            result.append (prefix + "if (" + bed.getFlag (pointer + "flags", false, bed.newborn) + ") shouldConnect = true;\n");
        }
    }
}
