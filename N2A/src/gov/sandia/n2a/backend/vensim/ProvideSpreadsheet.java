/*
Copyright 2021-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import gov.sandia.n2a.backend.c.BackendC;
import gov.sandia.n2a.backend.c.Compiler;
import gov.sandia.n2a.backend.c.CompilerFactory;
import gov.sandia.n2a.backend.c.JobC;
import gov.sandia.n2a.backend.c.ProvideOperator;
import gov.sandia.n2a.backend.c.RendererC;
import gov.sandia.n2a.eqset.EquationSet.NonzeroIterable;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;

public class ProvideSpreadsheet implements ProvideOperator
{
    protected static Map<Host,Set<String>> runtimeBuilt = new HashMap<Host,Set<String>> ();  // collection of Hosts for which runtime has already been checked/built during this session

    @Override
    public boolean rebuildRuntime (JobC job) throws Exception
    {
        Host env         = job.env;
        Path resourceDir = env.getResourceDir ();
        Path runtimeDir  = resourceDir.resolve ("backend").resolve ("vensim");
        Path localJobDir = job.localJobDir;

        boolean changed = false;
        if (env.config.getFlag ("backend", "c", "compilerChanged"))
        {
            changed = true;
            runtimeBuilt.remove (env);
        }
        Set<String> runtimes = runtimeBuilt.get (env);
        if (runtimes == null)
        {
            boolean updatedFiles = JobC.unpackRuntime
            (
                ProvideSpreadsheet.class, job.job, runtimeDir, "runtime/",
                "miniz.c",
                "miniz.h",
                "pugiconfig.hpp",
                "pugixml.cpp",
                "pugixml.hpp",
                "spreadsheet.cc",
                "spreadsheet.h",
                "spreadsheet.tcc"
            );
            if (updatedFiles) changed = true;
            runtimes = new HashSet<String> ();
            runtimeBuilt.put (env, runtimes);
        }

        if (changed)
        {
            runtimes.clear ();
            try (DirectoryStream<Path> list = Files.newDirectoryStream (runtimeDir))
            {
                for (Path file : list)
                {
                    if (file.getFileName ().toString ().endsWith (".o"))
                    {
                        job.job.set ("deleting " + file, "status");
                        Files.delete (file);
                    }
                }
            }
            catch (IOException e) {}
        }

        String objectName = job.objectName ("spreadsheet");
        if (runtimes.contains (objectName)) return false;

        Path object = runtimeDir.resolve (objectName);
        boolean buildNeeded =  ! Files.exists (object);
        if (buildNeeded)
        {
            job.job.set ("Compiling " + object, "status");

            CompilerFactory factory = BackendC.getFactory (env);
            Compiler c = factory.make (localJobDir);
            if (job.shared) c.setShared ();
            if (job.debug ) c.setDebug ();
            if (job.gprof ) c.setProfiling ();
            c.addInclude (runtimeDir);
            c.addInclude (job.runtimeDir);  // C runtime
            c.addDefine ("n2a_T", job.T);
            if (job.T.contains ("int")) c.addDefine ("n2a_FP");
            if (job.tls) c.addDefine ("n2a_TLS");
            c.setOutput (object);
            c.addSource (runtimeDir.resolve ("spreadsheet.cc"));

            Path out = c.compile ();
            Files.delete (out);
        }

        runtimes.add (objectName);
        return buildNeeded;
    }

    @Override
    public Path library (JobC job) throws Exception
    {
        Host env         = job.env;
        Path resourceDir = env.getResourceDir ();
        Path runtimeDir  = resourceDir.resolve ("backend").resolve ("vensim");
        return runtimeDir.resolve (job.objectName ("spreadsheet"));
    }

    @Override
    public Path include (JobC job) throws Exception
    {
        Path resourceDir = job.env.getResourceDir ();
        Path runtimeDir  = resourceDir.resolve ("backend").resolve ("vensim");
        return runtimeDir.resolve ("spreadsheet.h");
    }

    @Override
    public Boolean render (RendererC context, Operator op)
    {
        if (! (op instanceof Spreadsheet)) return null;
        Spreadsheet s = (Spreadsheet) op;
        StringBuilder result = context.result;

        String call = "get";
        String mode = s.getMode ();
        int length = s.operands.length;
        if (! mode.isEmpty ())
        {
            // The way "spreadsheet" is currently defined, the last string in parameters
            // can be mode or cell anchor or prefix. Since it is so ambiguous, we only
            // take away the last parameter if we positively identify a mode keyword.
            boolean found = false;
            for (String p : mode.split (","))
            {
                p = p.trim ();
                switch (p)
                {
                    case "rows":
                    case "columns":
                    case "rowsInColumn":
                    case "columnsInRow":
                        call = p;
                        found = true;
                        break;
                    default:
                        if (p.startsWith ("median")) found = true;
                }
            }
            if (found) length--;
        }

        int shift = s.exponent - s.exponentNext;
        if (context.useExponent  &&  shift != 0) result.append ("(");

        result.append (s.name + "->" + call + " (");
        if (length > 1)
        {
            s.operands[1].render (context);  // anchor cell
        }
        for (int i = 2; i < length; i++)
        {
            result.append (", ");
            s.operands[i].render (context);
        }
        result.append (")");

        if (context.useExponent  &&  shift != 0) result.append (RendererC.printShift (shift) + ")");
        return true;
    }

    @Override
    public Boolean assignNames (RendererC renderer, Operator op)
    {
        if (! (op instanceof Spreadsheet)) return null;
        Spreadsheet s = (Spreadsheet) op;
        JobC job = renderer.job;
        Operator operand0 = s.operands[0];
        if (operand0 instanceof Constant)  // Static file name
        {
            Constant c = (Constant) operand0;
            String fileName = ((Text) c.value).value;
            s.name = job.extensionNames.get (fileName);
            if (s.name == null)
            {
                s.name = "Spreadsheet" + job.extensionNames.size ();
                job.extensionNames.put (fileName, s.name);
                job.mainExtension.add (s);
            }
        }
        else  // Dynamic file name
        {
            job.extensionNames.put (op,       s.name     = "Spreadsheet" + job.extensionNames.size ());
            job.stringNames   .put (operand0, s.fileName = "fileName"    + job.stringNames   .size ());
            if (operand0 instanceof Add) ((Add) operand0).name = s.fileName;
            else 
            {
                Backend.err.get ().println ("ERROR: File name must be a string expression.");
                throw new AbortRun ();
            }
        }
        return true;  // Functions can contain other functions, so continue recursion.
    }

    @Override
    public void generateStatic (RendererC context)
    {
        JobC job = context.job;
        String thread_local = job.tls ? "thread_local " : "";
        for (Operator op : job.mainExtension)
        {
            if (op instanceof Spreadsheet)
            {
                Spreadsheet s = (Spreadsheet) op;
                context.result.append (thread_local + "Spreadsheet<" + job.T + "> * " + s.name + ";\n");
            }
        }
    }

    @Override
    public void generateMainInitializers (RendererC context)
    {
        JobC job = context.job;
        for (Operator op : job.mainExtension)
        {
            if (op instanceof Spreadsheet)
            {
                Spreadsheet s = (Spreadsheet) op;
                context.result.append ("  " + s.name + " = spreadsheetHelper<" + job.T + "> (\"" + s.operands[0].getString () + "\"");
                if (job.T.contains ("int")) context.result.append (", " + s.exponent);
                context.result.append (");\n");
            }
        }
    }

    @Override
    public Boolean prepareStaticObjects (Operator op, RendererC context, String pad)
    {
        return null;
    }

    @Override
    public Boolean prepareDynamicObjects (Operator op, RendererC context, boolean init,String pad)
    {
        if (! (op instanceof Spreadsheet)) return null;
        Spreadsheet s = (Spreadsheet) op;
        if (! (s.operands[0] instanceof Constant))
        {
            JobC job = context.job;
            context.result.append (pad + "Spreadsheet<" + job.T + "> * " + s.name + " = spreadsheetHelper<" + job.T + "> (" + s.fileName);
            if (job.T.contains ("int")) context.result.append (", " + s.exponent);
            context.result.append (");\n");
        }
        return true;
    }

    @Override
    public boolean getIterator (NonzeroIterable nzit, RendererC context)
    {
        if (! (nzit instanceof Spreadsheet)) return false;
        Spreadsheet s = (Spreadsheet) nzit;
        String anchor = s.operands[1].getString ();  // No need to render.
        context.result.append (s.name + "->getIterator (\"" + anchor + "\");\n");
        return true;
    }
}
